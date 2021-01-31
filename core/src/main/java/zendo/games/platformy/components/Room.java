package zendo.games.platformy.components;

import com.badlogic.gdx.maps.tiled.TiledMap;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Tilemap;
import zendo.games.zenlib.ecs.Component;
import zendo.games.zenlib.utils.Point;

public class Room extends Component {

    public TiledMap map = null;
    public Tilemap tilemap = null;
    public Collider solids = null;
    public Point size = Point.zero();
    public Point coord = Point.zero();

    @Override
    public <T extends Component> void copyFrom(T other) {
        super.copyFrom(other);
        if (other instanceof Room) {
            Room room = (Room) other;
            this.map     = room.map;
            this.tilemap = room.tilemap;
            this.solids  = room.solids;
            this.size    = room.size;
            this.coord   = room.coord;
        }
    }

}
