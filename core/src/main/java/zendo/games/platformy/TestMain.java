package zendo.games.platformy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import zendo.games.platformy.components.Enemy;
import zendo.games.platformy.components.Player;
import zendo.games.platformy.components.Room;
import zendo.games.platformy.config.Config;
import zendo.games.platformy.config.Debug;
import zendo.games.zenlib.Game;
import zendo.games.zenlib.components.Animator;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Mover;
import zendo.games.zenlib.ecs.Entity;
import zendo.games.zenlib.ecs.World;
import zendo.games.zenlib.utils.Calc;
import zendo.games.zenlib.utils.Point;
import zendo.games.zenlib.utils.RectI;
import zendo.games.zenlib.utils.Time;

public class TestMain implements Game {

    SpriteBatch batch;
    ShapeRenderer shapes;

    FrameBuffer frameBuffer;
    Texture frameBufferTexture;
    TextureRegion frameBufferRegion;

    Matrix4 screenProjection;
    OrthographicCamera worldCamera;

    World world;

    Room room;
    Room lastRoom;
    Array<Entity> lastRoomEnemies;
    Point transitionLastCameraTarget;
    Point transitionNextCameraTarget;
    float transitionEase;
    boolean transitioning;

    final float transition_duration = 0.66f;

    @Override
    public void init() {
        Assets.load();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();

        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Config.framebuffer_width, Config.framebuffer_height, false);
        frameBufferTexture = frameBuffer.getColorBufferTexture();
        frameBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        frameBufferRegion = new TextureRegion(frameBufferTexture);
        frameBufferRegion.flip(false, true);

        screenProjection = new Matrix4().setToOrtho2D(0, 0, Config.window_width, Config.window_height);

        worldCamera = new OrthographicCamera();
        worldCamera.setToOrtho(false, Config.framebuffer_width, Config.framebuffer_height);
        worldCamera.update();

        world = new World();

        Assets.loadRooms(world);
        room = Assets.findRoom(world, Point.at(0, 0));

        Player player = world.first(Player.class);
        worldCamera.position.set(player.entity().position.x, player.entity().position.y, 0);
        worldCamera.update();

        lastRoomEnemies = new Array<>();
        transitionLastCameraTarget = Point.zero();
        transitionNextCameraTarget = Point.zero();
        transitioning = false;
        transitionEase = 0;
    }

    @Override
    public void update(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        // debug input handling
        {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
                Debug.draw_colliders = !Debug.draw_colliders;
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
                Debug.draw_entities = !Debug.draw_entities;
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
                Debug.draw_origin = !Debug.draw_origin;
            }

            if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
                Debug.frame_step = !Debug.frame_step;
            }

            if (Debug.frame_step && !Gdx.input.isKeyJustPressed(Debug.frame_step_key)) {
                return;
            }
        }

        Player player = world.first(Player.class);
        Mover mover = player.get(Mover.class);

        // find camera targets to follow player
        RectI roomBounds = RectI.at(
                room.entity().position.x,
                room.entity().position.y,
                room.size.x, room.size.y);
        int halfViewW = (int) worldCamera.viewportWidth / 2;
        int halfViewH = (int) worldCamera.viewportHeight / 2;
        float targetX;
        float targetY;

        if (transitioning) {
            // room transition update
            transitionEase = Calc.approach(transitionEase, 1f, Time.delta / transition_duration);

            // lerp camera position
            targetX = transitionLastCameraTarget.x + (transitionNextCameraTarget.x - transitionLastCameraTarget.x) * Interpolation.pow5.apply(transitionEase);
            targetY = transitionLastCameraTarget.y + (transitionNextCameraTarget.y - transitionLastCameraTarget.y) * Interpolation.pow5.apply(transitionEase);
            worldCamera.position.set(targetX, targetY, 0);

            // finish transition
            if (transitionEase >= 1) {
                // delete old enemies
                for (Entity entity : lastRoomEnemies) {
                    world.destroyEntity(entity);
                }

                Time.pause_for(0.2f);
                transitioning = false;
            }
        } else {
            // normal update
            world.update(dt);

            // check for room transition if player is moving out of this room
            int xOffset = 0;
            int yOffset = 0;
            if      (mover.speed.x > 0 && player.entity().position.x > roomBounds.right())  xOffset =  1;
            else if (mover.speed.x < 0 && player.entity().position.x < roomBounds.left())   xOffset = -1;
            if      (mover.speed.y > 0 && player.entity().position.y > roomBounds.top())    yOffset =  1;
            else if (mover.speed.y < 0 && player.entity().position.y < roomBounds.bottom()) yOffset = -1;

            // only perform next room lookup if player actually moved into another room
            if (xOffset != 0 || yOffset != 0) {
                // is there a room where the player is trying to move?
                Room nextRoom = Assets.findRoom(world, Point.at(room.coord.x + xOffset, room.coord.y + yOffset));
                if (nextRoom == null) {
                    // no new room in this direction, just clamp the player position to this room's bounds
                    player.entity().position.x = Calc.clampInt(player.entity().position.x, roomBounds.x, roomBounds.x + roomBounds.w);
                    player.entity().position.y = Calc.clampInt(player.entity().position.y, roomBounds.y, roomBounds.y + roomBounds.h);

                    // camera follows player
                    targetX = Calc.ceiling(Calc.approach(worldCamera.position.x, player.entity().position.x, 400 * dt));
                    targetY = Calc.ceiling(Calc.approach(worldCamera.position.y, player.entity().position.y, 200 * dt));

                    // keep camera in bounds
                    targetX = Calc.clampInt((int) targetX, roomBounds.x + halfViewW, roomBounds.x + roomBounds.w - halfViewW);
                    targetY = Calc.clampInt((int) targetY, roomBounds.y + halfViewH, roomBounds.y + roomBounds.h - halfViewH);
                    worldCamera.position.set(targetX, targetY, 0);
                } else {
                    // player is trying to move into a new room
                    // start a transition to the next room
                    transitioning = true;
                    transitionEase = 0;
                    lastRoom = room;
                    room = nextRoom;
                    roomBounds = RectI.at(
                            nextRoom.entity().position.x,
                            nextRoom.entity().position.y,
                            nextRoom.size.x, nextRoom.size.y
                    );

                    // save the current camera position
                    transitionLastCameraTarget.set((int) worldCamera.position.x, (int) worldCamera.position.y);

                    // save the target camera position
                    // TODO: the link position is the bottom left corner of a tile so it's a little off
                    Room.Link link = room.findLink(lastRoom.coord);
                    targetX = room.entity().position.x + link.position.x;
                    targetY = room.entity().position.y + link.position.y - 16;
                    targetX = Calc.clampInt((int) targetX, roomBounds.x + halfViewW, roomBounds.x + roomBounds.w - halfViewW);
                    targetY = Calc.clampInt((int) targetY, roomBounds.y + halfViewH, roomBounds.y + roomBounds.h - halfViewH);
                    transitionNextCameraTarget.set((int) targetX, (int) targetY);

                    // save entities from last room to be removed when transition is complete
                    // TODO: entities aren't really tied to their room right now,
                    //       have to decide whether to do that (add another component or maybe a room coord field?)
                    lastRoomEnemies.clear();
                    Entity entity = world.firstEntity();
                    while (entity != null) {
                        if (entity.get(Enemy.class) != null) {
                            lastRoomEnemies.add(entity);
                        }
                        entity = entity.next();
                    }

                    // disable any squishing/stretching that the player is currently doing
                    Animator anim = player.get(Animator.class);
                    anim.scale.set(player.getFacing(), 1);
                }
            } else {
                // just normal player movement, no room transition

                // TODO: improve camera following behavior

                // camera follows player
                targetX = Calc.ceiling(Calc.approach(worldCamera.position.x, player.entity().position.x, 400 * dt));
                targetY = Calc.ceiling(Calc.approach(worldCamera.position.y, player.entity().position.y, 200 * dt));

                // keep camera in bounds
                targetX = Calc.clampInt((int) targetX, roomBounds.x + halfViewW, roomBounds.x + roomBounds.w - halfViewW);
                targetY = Calc.clampInt((int) targetY, roomBounds.y + halfViewH, roomBounds.y + roomBounds.h - halfViewH);
                worldCamera.position.set(targetX, targetY, 0);
            }
        }

        worldCamera.update();
    }

    @Override
    public void render() {
        renderWorldIntoFramebuffer();
        renderFramebufferIntoWindow();
    }

    @Override
    public void shutdown() {
        Assets.unload(world);

        frameBufferTexture.dispose();
        frameBuffer.dispose();
        batch.dispose();
    }

    // ------------------------------------------------------------------------

    private void renderWorldIntoFramebuffer() {
        frameBuffer.begin();
        {
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 0f);
            Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                world.render(batch);
            }
            batch.end();

            shapes.setProjectionMatrix(worldCamera.combined);
            shapes.setAutoShapeType(true);
            shapes.begin();
            {
                // colliders
                if (Debug.draw_colliders) {
                    shapes.setColor(Color.RED);
                    Collider collider = world.first(Collider.class);
                    while (collider != null) {
                        collider.render(shapes);
                        collider = (Collider) collider.next();
                    }
                    shapes.setColor(Color.WHITE);
                }

                // entities
                if (Debug.draw_entities) {
                    shapes.setColor(Color.YELLOW);
                    Entity entity = world.firstEntity();
                    while (entity != null) {
                        shapes.point(entity.position.x, entity.position.y, 0);
                        entity = entity.next();
                    }
                    shapes.setColor(Color.WHITE);
                }

                // origin coord axis
                if (Debug.draw_origin) {
                    shapes.setColor(Color.BLUE);
                    shapes.rectLine(0, 0, 10, 0, 1);
                    shapes.setColor(Color.GREEN);
                    shapes.rectLine(0, 0, 0, 10, 1);
                    shapes.setColor(Color.RED);
                    shapes.circle(0, 0, 1);
                    shapes.setColor(Color.WHITE);
                }
            }
            shapes.end();
        }
        frameBuffer.end();
    }

    private void renderFramebufferIntoWindow() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(screenProjection);
        batch.begin();
        {
            // background
            batch.setColor(Color.SKY);
            batch.draw(Assets.pixel, 0, 0, Config.window_width, Config.window_height);
            batch.setColor(Color.WHITE);

            // composite scene
            batch.draw(frameBufferRegion, 0, 0, Config.window_width, Config.window_height);

            // ui
            if (transitioning) {
                batch.setColor(Color.LIME);
                batch.draw(Assets.pixel, 10, 10, 10, 10);
                batch.setColor(Color.WHITE);
            }
        }
        batch.end();
    }

}