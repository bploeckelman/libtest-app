package zendo.games.platformy.components;

import zendo.games.zenlib.ecs.Component;
import zendo.games.zenlib.utils.Calc;
import zendo.games.zenlib.utils.RectI;

public class Enemy extends Component {

    public Room room;
    private RectI roomBounds;

    public Enemy() {
        super();
    }

    public Enemy(Room room) {
        super();
        setRoom(room);
    }

    public void setRoom(Room room) {
        this.room = room;
        this.roomBounds = RectI.at(
                room.entity().position.x,
                room.entity().position.y,
                room.size.x, room.size.y);
    }

    @Override
    public void reset() {
        super.reset();
        this.room = null;
        this.roomBounds = null;
    }

    @Override
    public <T extends Component> void copyFrom(T other) {
        super.copyFrom(other);
        if (other instanceof Enemy) {
            Enemy enemy = (Enemy) other;
            this.room = enemy.room;
            this.roomBounds = enemy.roomBounds;
        }
    }

    @Override
    public void update(float dt) {
        // TODO: fix RectI bottom/top, or add a flag to calculate y-up if the y-down is needed elsewhere
        entity().position.x = Calc.clampInt(entity.position.x, roomBounds.left(), roomBounds.right());
        entity().position.y = Calc.clampInt(entity.position.y, roomBounds.top(), roomBounds.bottom());
    }

}
