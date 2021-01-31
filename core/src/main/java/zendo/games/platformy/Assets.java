package zendo.games.platformy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.utils.Array;
import zendo.games.platformy.components.Room;
import zendo.games.zenlib.assets.Content;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Tilemap;
import zendo.games.zenlib.ecs.Entity;
import zendo.games.zenlib.ecs.Mask;
import zendo.games.zenlib.ecs.World;
import zendo.games.zenlib.utils.Point;

public class Assets extends Content {

    private static final String tag = Assets.class.getSimpleName();

    public static BitmapFont font;
    public static Texture pixel;
    private static TextureAtlas atlas;

    public static Array<Room> rooms;

    public static void load() {
        font = new BitmapFont();
        pixel = new Texture("pixel.png");
        pixel.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // load sprites
        atlas = new TextureAtlas("atlas/sprites.atlas");
        sprites.addAll(
                  Content.loadSprite("sprites/player.json", atlas)
                , Content.loadSprite("sprites/blob.json", atlas)
                , Content.loadSprite("sprites/pop.json", atlas)
        );
    }

    public static void unload() {
        Content.unload();
        Assets.unloadRooms();

        atlas.dispose();
        pixel.dispose();
        font.dispose();
    }

    public static void loadRooms(World world) {
        if (rooms == null) {
            rooms = new Array<>();
        } else {
            Assets.unloadRooms();
        }

        rooms.add(loadRoom(Point.at(0, 0), world));
        rooms.add(loadRoom(Point.at(1, 0), world));
    }

    // TODO: maybe remove rooms array and just do lookup through world for Room components?
    public static Room findRoom(Point coord) {
        if (rooms == null) {
            return null;
        }

        for (Room room : rooms) {
            if (room.coord.x == coord.x && room.coord.y == coord.y) {
                return room;
            }
        }
        return null;
    }

    public static void unloadRooms() {
        for (Room room : rooms) {
            if (room.map != null) {
                room.map.dispose();
            }
            if (room.tilemap != null ){
                room.tilemap.destroy();
            }
            if (room.solids != null) {
                room.solids.destroy();
            }
        }
        rooms.clear();
    }

    public static Room loadRoom(Point coord, World world) {
        String filename = "maps/room_" + coord.x + "x" + coord.y + ".tmx";
        if (!Gdx.files.internal(filename).exists()) {
            Gdx.app.log(tag, "Unable to load room, map file does not exist: " + filename);
            return null;
        }

        // create a map entity
        Entity entity = world.addEntity();

        // add a Room component
        Room room = entity.add(new Room(), Room.class);
        {
            // load the map
            room.map = new TmxMapLoader().load(filename);

            room.coord.set(coord.x, coord.y);

            // get tiled map parameters
            TiledMapTileLayer collisionLayer = (TiledMapTileLayer) room.map.getLayers().get("collision");
            int tileSize = collisionLayer.getTileWidth();
            int columns = collisionLayer.getWidth();
            int rows = collisionLayer.getHeight();

            // set the room size (in pixels)
            room.size.set(columns * tileSize, rows * tileSize);

            // find the original by checking for neighbors
            // TODO: add map objects to tie adjacent rooms together

            // TODO: set entity.position

            // add a tilemap component for textures
            room.tilemap = entity.add(new Tilemap(), Tilemap.class);
            room.tilemap.init(tileSize, columns, rows);

            // add a collider component
            room.solids = entity.add(Collider.makeGrid(tileSize, columns, rows), Collider.class);
            room.solids.mask = Mask.solid;

            // parse the tiled map layers
            for (MapLayer layer : room.map.getLayers()) {
                // parse tile layers
                if (layer instanceof TiledMapTileLayer) {
                    TiledMapTileLayer tileLayer = (TiledMapTileLayer) layer;

                    for (int x = 0; x < columns; x++) {
                        for (int y = 0; y < rows; y++) {
                            // skip empty cells
                            TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                            if (cell == null) continue;

                            // determine what type of layer this is
                            boolean isCollision = "collision".equals(layer.getName());
                            boolean isBackground = "background".equals(layer.getName());

                            // only collision layer tiles are used to populate the collider grid
                            if (isCollision) {
                                room.solids.setCell(x, y, true);
                            }

                            // both collision and background layers are used to set tile textures
                            if (isCollision || isBackground) {
                                room.tilemap.setCell(x, y, cell.getTile().getTextureRegion());
                            }
                        }
                    }
                }
                // parse objects layer
                else if ("objects".equals(layer.getName())) {
                    Array<TiledMapTileMapObject> objects = layer.getObjects().getByType(TiledMapTileMapObject.class);
                    for (TiledMapTileMapObject object : objects) {
                        // parse position property from object
                        // scale to specified tileSize in case it's different than the tiled map tile size
                        // this way the scale of the map onscreen can be changed by adjusting the tileSize parameter
                        Point position = Point.at(
                                (int) (object.getX() / collisionLayer.getTileWidth()) * tileSize,
                                (int) (object.getY() / collisionLayer.getTileHeight()) * tileSize);

                        // parse the object type
                        String type = (String) object.getProperties().get("type");
                        if ("spawner".equals(type)) {
                            // figure out what to spawn and do so
                            String target = (String) object.getProperties().get("target");
                            switch (target) {
                                case "player":
                                    Factory.player(world, position);
                                    break;
                                case "blob":
                                    Factory.blob(world, position);
                                    break;
                            }
                        }
                    }
                }
            }
        }
        return room;
    }

}
