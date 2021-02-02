package zendo.games.platformy.components;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Array;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Tilemap;
import zendo.games.zenlib.ecs.Component;
import zendo.games.zenlib.ecs.Entity;
import zendo.games.zenlib.utils.Point;

public class Room extends Component {

    public TiledMap map = null;
    public Tilemap tilemap = null;
    public Collider solids = null;
    public Point size = Point.zero();
    public Point coord = Point.zero();
    public Array<Link> links = new Array<>();
    public Array<Entity> platforms = new Array<>();

    public static class Link {
        public Point targetCoord;
        public Point position;
        public Link(Point targetCoord, Point position) {
            this.targetCoord = targetCoord;
            this.position = position;
        }
        public Link(int x, int y, int tileX, int tileY) {
            this.targetCoord = Point.at(x, y);
            this.position = Point.at(tileX, tileY);
        }
    }

    public Link findLink(Point coord) {
        for (Link link : links) {
            if (link.targetCoord.x == coord.x && link.targetCoord.y == coord.y) {
                return link;
            }
        }
        return null;
    }

    @Override
    public <T extends Component> void copyFrom(T other) {
        super.copyFrom(other);
        if (other instanceof Room) {
            Room room = (Room) other;
            this.map       = room.map;
            this.tilemap   = room.tilemap;
            this.solids    = room.solids;
            this.size      = room.size;
            this.coord     = room.coord;
            this.platforms = room.platforms;
        }
    }

}
