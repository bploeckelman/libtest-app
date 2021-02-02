package zendo.games.platformy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.MapLayer;
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
import zendo.games.zenlib.utils.Calc;
import zendo.games.zenlib.utils.Point;

public class Assets extends Content {

    private static final String tag = Assets.class.getSimpleName();

    public static BitmapFont font;
    public static Texture pixel;
    private static TextureAtlas atlas;

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

    public static void unload(World world) {
        Assets.unloadRooms(world);

        Content.unload();

        atlas.dispose();
        pixel.dispose();
        font.dispose();
    }

    public static Room findRoom(World world, Point coord) {
        Room room = world.first(Room.class);
        while (room != null) {
            if (room.coord.x == coord.x && room.coord.y == coord.y) {
                return room;
            }
            room = (Room) room.next();
        }
        return null;
    }

    public static void loadRooms(World world) {
        loadRoom(Point.at(-1, 0), world);
        loadRoom(Point.at(0, 0), world);
        loadRoom(Point.at(1, 0), world);

        linkRooms(world);
    }

    private static void linkRooms(World world) {
        // TODO: should probably make sure the links are valid
        // position rooms based on their links
        Room srcRoom = world.first(Room.class);
        while (srcRoom != null) {
            for (Room.Link linkSrcToDst : srcRoom.links) {
                Room dstRoom = findRoom(world, linkSrcToDst.targetCoord);
                if (dstRoom == null) {
                    Gdx.app.log(tag, "Invalid room-link, "
                            + "target not found: " + linkSrcToDst.targetCoord.x + ", " + linkSrcToDst.targetCoord.y + " "
                            + "from room " + srcRoom.coord.x + ", " + srcRoom.coord.y + "; skipping");
                } else {
                    // find corresponding link in roomB so we only process a link once
                    Room.Link linkDstToSrc = dstRoom.findLink(srcRoom.coord);
                    if (linkDstToSrc != null) {
                        // this is the corresponding link,
                        // position dstRoom relative to srcRoom
                        // making linkSrcToDst and linkDstToSrc positions adjacent

                        // determine the orientations of each link within it's room
                        Point orientSrcToDst = Point.at(
                                dstRoom.coord.x - srcRoom.coord.x,
                                dstRoom.coord.y - srcRoom.coord.y);

                        if (Calc.abs(orientSrcToDst.x) > 1 || Calc.abs(orientSrcToDst.y) > 1) {
                            Gdx.app.log(tag, "Invalid room-link, "
                                    + "difference between srcRoom and dstRoom coords is > 1");
                        } else if (Calc.abs(orientSrcToDst.x) > 0 && Calc.abs(orientSrcToDst.y) > 0) {
                            Gdx.app.log(tag, "Invalid room-link, "
                                    + "src and dest rooms must differ only on one axis");
                        } else {
                            // use the orientation to position dstRoom relative to srcRoom
                            int tileSize = 16; // room.tilemap.tileSize

                            // dst to the right of src
                            if (orientSrcToDst.x > 0) {
                                dstRoom.entity().position.x = srcRoom.entity().position.x + linkSrcToDst.position.x + tileSize;
                                dstRoom.entity().position.y = srcRoom.entity().position.y + linkSrcToDst.position.y - linkDstToSrc.position.y;
                            }
                            // dst to the left of src
                            else if (orientSrcToDst.x < 0) {
                                dstRoom.entity().position.x = srcRoom.entity().position.x - dstRoom.size.x;
                                dstRoom.entity().position.y = srcRoom.entity().position.y + linkSrcToDst.position.y - linkDstToSrc.position.y;
                            }
                            // dst above src
                            else if (orientSrcToDst.y > 0) {
                                dstRoom.entity().position.x = 0;
                                dstRoom.entity().position.y = srcRoom.entity().position.y + linkSrcToDst.position.y + tileSize;
                            }
                            // dst below src
                            else if (orientSrcToDst.y < 0) {
                                dstRoom.entity().position.x = 0;
                                dstRoom.entity().position.y = srcRoom.entity().position.y - dstRoom.size.y;
                            }
                        }
                    }
                }
            }
            srcRoom = (Room) srcRoom.next();
        }
    }

    public static void unloadRooms(World world) {
        Room room = world.first(Room.class);
        while (room != null) {
            if (room.map != null) {
                room.map.dispose();
            }
            if (room.tilemap != null) {
                room.tilemap.destroy();
            }
            if (room.solids != null) {
                room.solids.destroy();
            }
            room.links.clear();
            room = (Room) room.next();
        }
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
                // note: object spawning is decoupled from other object parsing that occurs during this load
                //       as we need to be able to spawn all creatures for a room on demand at runtime
                else if ("objects".equals(layer.getName())) {
                    Array<TiledMapTileMapObject> objects = layer.getObjects().getByType(TiledMapTileMapObject.class);
                    for (TiledMapTileMapObject object : objects) {
                        // parse position property from object
                        // scale to specified tileSize in case it's different than the tiled map tile size
                        // this way the scale of the map onscreen can be changed by adjusting the tileSize parameter
                        Point position = Point.at(
                                (int) (object.getX() / collisionLayer.getTileWidth())  * tileSize,
                                (int) (object.getY() / collisionLayer.getTileHeight()) * tileSize);

                        // parse the object type
                        String type = (String) object.getProperties().get("type");
                        // note - spawner objects are triggered in Assets.spawnEntities()
                        if ("room-link".equals(type)) {
                            int x = object.getProperties().get("target-x", Integer.class);
                            int y = object.getProperties().get("target-y", Integer.class);
                            room.links.add(new Room.Link(x, y, position.x, position.y));
                        }
                    }
                }
            }
        }
        return room;
    }

    public static void spawnEntities(World world, Room room) {
        spawnEntities(world, room, false);
    }

    public static void spawnEntities(World world, Room room, boolean includingPlayer) {
        TiledMapTileLayer collisionLayer = (TiledMapTileLayer) room.map.getLayers().get("collision");
        int tileSize = collisionLayer.getTileWidth();

        // parse the tiled map object layer and trigger spawners
        for (MapLayer layer : room.map.getLayers()) {
            if ("objects".equals(layer.getName())) {
                Array<TiledMapTileMapObject> objects = layer.getObjects().getByType(TiledMapTileMapObject.class);
                for (TiledMapTileMapObject object : objects) {
                    // parse position property from object
                    // scale to specified tileSize in case it's different than the tiled map tile size
                    // this way the scale of the map onscreen can be changed by adjusting the tileSize parameter
                    Point position = Point.at(
                            room.entity().position.x + (int) (object.getX() / collisionLayer.getTileWidth())  * tileSize,
                            room.entity().position.y + (int) (object.getY() / collisionLayer.getTileHeight()) * tileSize);

                    // parse the object type
                    String type = (String) object.getProperties().get("type");
                    if ("spawner".equals(type)) {
                        // figure out what to spawn and do so
                        String target = (String) object.getProperties().get("target");
                        switch (target) {
                            case "player":
                                if (includingPlayer) {
                                    Factory.player(world, position);
                                }
                                break;
                            case "blob":
                                Factory.blob(world, position, room);
                                break;
                        }
                    }
                }
            }
        }
    }

}
