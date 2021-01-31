package zendo.games.platformy;

import com.badlogic.gdx.maps.tiled.TiledMap;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Tilemap;
import zendo.games.zenlib.utils.Point;

public class Room {

    public TiledMap map;
    public Tilemap tilemap;
    public Collider solids;

    public Point size = Point.zero();
    public Point origin = Point.zero();
    public Point coord = Point.zero();

}
