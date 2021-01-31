package zendo.games.platformy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import zendo.games.platformy.components.Player;
import zendo.games.platformy.config.Config;
import zendo.games.platformy.config.Debug;
import zendo.games.zenlib.Game;
import zendo.games.zenlib.assets.Content;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Mover;
import zendo.games.zenlib.components.Tilemap;
import zendo.games.zenlib.ecs.Entity;
import zendo.games.zenlib.ecs.Mask;
import zendo.games.zenlib.ecs.World;
import zendo.games.zenlib.utils.Calc;
import zendo.games.zenlib.utils.Point;
import zendo.games.zenlib.utils.RectI;

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
        room = Assets.findRoom(Point.at(0, 0));

        Player player = world.first(Player.class);
        worldCamera.position.set(player.entity().position.x, player.entity().position.y, 0);
        worldCamera.update();
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

        world.update(dt);

        // TODO: change to check for another room attached to this room at the out-of-bounds point and transition to a new room if one exists

        // keep player in bounds
//        Collider.Grid solids = world.first(Tilemap.class).entity().get(Collider.class).getGrid();
        Collider.Grid solids = room.solids.getGrid();
        RectI bounds = RectI.at(0, 0, solids.columns * solids.tileSize, solids.rows * solids.tileSize);

        Player player = world.first(Player.class);
//        player.entity().position.x = Calc.clampInt(player.entity().position.x, bounds.x, bounds.x + bounds.w);
//        player.entity().position.y = Calc.clampInt(player.entity().position.y, bounds.y, bounds.y + bounds.h);

        // find camera targets to follow player
        // NOTE: this is a little silly because depending which way the player is moving ceiling/floor tracks quickly while the other doesn't
        float targetX = (player.get(Mover.class).speed.x > 0)
                ? Calc.ceiling(Calc.approach(worldCamera.position.x, player.entity().position.x, 400 * dt))
                : Calc.floor  (Calc.approach(worldCamera.position.x, player.entity().position.x, 400 * dt));
        float targetY = Calc.ceiling(Calc.approach(worldCamera.position.y, player.entity().position.y, 100 * dt));

//        // keep camera in bounds
//        int halfViewW = (int) worldCamera.viewportWidth / 2;
//        int halfViewH = (int) worldCamera.viewportHeight / 2;
//        targetX = Calc.clampInt((int) targetX, bounds.x + halfViewW, bounds.x + bounds.w - halfViewW);
//        targetY = Calc.clampInt((int) targetY, bounds.y + halfViewH, bounds.y + bounds.h - halfViewH);

        worldCamera.position.set(targetX, targetY, 0);
        worldCamera.update();
    }

    @Override
    public void render() {
        renderWorldIntoFramebuffer();
        renderFramebufferIntoWindow();
    }

    @Override
    public void shutdown() {
        frameBufferTexture.dispose();
        frameBuffer.dispose();
        batch.dispose();
        Assets.unload();
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
        }
        batch.end();
    }

}