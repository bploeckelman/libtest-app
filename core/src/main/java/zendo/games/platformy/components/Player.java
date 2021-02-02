package zendo.games.platformy.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import zendo.games.zenlib.components.Animator;
import zendo.games.zenlib.components.Collider;
import zendo.games.zenlib.components.Mover;
import zendo.games.zenlib.ecs.Component;
import zendo.games.zenlib.ecs.Mask;
import zendo.games.zenlib.utils.Calc;
import zendo.games.zenlib.utils.RectI;
import zendo.games.zenlib.utils.Time;

public class Player extends Component {

    public static final float gravity = -450;

    private static final float ground_accel = 500;
    private static final float ground_accel_run = 800;
    private static final float friction = 800;
    private static final float max_ground_speed = 100;
    private static final float max_ground_speed_run = 200;
    private static final float jump_impulse = 130;
    private static final float jump_time = 0.18f;
    private static final float hurt_friction = 200;
    private static final float hurt_duration = 0.5f;
    private static final float invincible_duration = 1.5f;

    enum State {
        normal, attack, hurt
    }

    private int health = 3;
    private int facing = 1;
    private float jumpTimer = 0;
    private float attackTimer = 0;
    private float hurtTimer = 0;
    private float invincibleTimer = 0;
    private boolean onGround = false;
    private State state = State.normal;
    private Collider attackCollider = null;

    private static class InputState {
        int move_dir = 0;
        boolean run_held = false;
        boolean jump_held = false;
        boolean jump = false;
        boolean attack = false;
    }
    private final InputState input = new InputState();

    private void updateInputState() {
        Array<Controller> controllers = Controllers.getControllers();
        Controller controller = controllers.isEmpty() ? null : controllers.get(0);

        int controller_button_a    = (controller == null) ? 0 : controller.getMapping().buttonA;
        int controller_button_x    = (controller == null) ? 0 : controller.getMapping().buttonX;
        int controller_button_r1   = (controller == null) ? 0 : controller.getMapping().buttonR1;
        int controller_axis_left_x = (controller == null) ? 0 : controller.getMapping().axisLeftX;

        float controller_dead_zone = 0.3f;
        float controller_axis_left_x_value          = (controller == null) ? 0 : controller.getAxis(controller_axis_left_x);
        boolean controller_axis_left_x_in_dead_zone = (controller != null && Calc.abs(controller_axis_left_x_value) <= controller_dead_zone);

        // move direction
        input.move_dir = 0;
        if      (Gdx.input.isKeyPressed(Input.Keys.LEFT)  || (controller != null && !controller_axis_left_x_in_dead_zone && controller_axis_left_x_value < 0)) input.move_dir = -1;
        else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || (controller != null && !controller_axis_left_x_in_dead_zone && controller_axis_left_x_value > 0)) input.move_dir =  1;

        // jump input
        input.jump = false;
        input.jump_held = false;
        // TODO: need additional state var to get 'just pressed' for controller button
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || (controller != null && controller.getButton(controller_button_a))) input.jump = true;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)     || (controller != null && controller.getButton(controller_button_a))) input.jump_held = true;

        // run input
        input.run_held = false;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || (controller != null && controller.getButton(controller_button_r1))) input.run_held = true;

        // attack input
        input.attack = false;
        // TODO: need additional state var to get 'just pressed' for controller button
        if (Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT) || (controller != null && controller.getButton(controller_button_x))) input.attack = true;
    }

    public int getFacing() {
        return facing;
    }

    @Override
    public void update(float dt) {
        // get input
        updateInputState();

        // get components
        Animator anim = entity().get(Animator.class);
        Mover mover = entity().get(Mover.class);

        boolean wasOnGround = onGround;
        onGround = mover.onGround();

        // sprite
        {
            // landing squish
            if (!wasOnGround && onGround) {
                anim.scale.set(facing * 1.5f, 0.7f);
            }

            // lerp scale back to one
            anim.scale.set(
                    Calc.approach(anim.scale.x, facing, 4 * dt),
                    Calc.approach(anim.scale.y, 1, 4 * dt)
            );

            // facing
            anim.scale.x = Calc.abs(anim.scale.x) * facing;
        }

        // NORMAL STATE -----------------------------------
        if (state == State.normal) {
            // stopped
            if (onGround) {
                if (input.move_dir != 0) {
                    anim.play("run");
//                    anim.speed = (input.run_held) ? 1.4f : 1;
                } else {
                    anim.play("idle");
                }
            } else {
                if (mover.speed.y > 0) {
                    anim.play("jump");
                } else {
                    anim.play("fall");
                }
            }

            // horizontal movement
            {
                // acceleration
                float accel = (input.run_held) ? ground_accel_run : ground_accel;
                mover.speed.x += input.move_dir * accel * dt;

                // max speed
                float max = (input.run_held) ? max_ground_speed_run : max_ground_speed;
                if (Calc.abs(mover.speed.x) > max) {
                    mover.speed.x = Calc.approach(mover.speed.x, Calc.sign(mover.speed.x) * max, 2000 * dt);
                }

                // friction
                if (input.move_dir == 0 && onGround) {
                    mover.speed.x = Calc.approach(mover.speed.x, 0, friction * dt);
                }

                // facing direction
                if (input.move_dir != 0) {
                    facing = input.move_dir;
                }
            }

            // trigger a jump
            {
                if (input.jump && onGround) {
                    // squoosh on jomp
                    anim.scale.set(facing * 0.65f, 1.4f);

                    jumpTimer = jump_time;
                }
            }

            // begin attacking
            if (input.attack) {
                state = State.attack;
                attackTimer = 0;

                if (attackCollider == null) {
                    // update the attack collider rect during attack state based on what anim frame we're in
                    attackCollider = entity().add(Collider.makeRect(new RectI()), Collider.class);
                    attackCollider.mask = Mask.player_attack;
                }

                if (onGround) {
                    mover.stopX();
                }
            }
        }
        // ATTACK STATE -----------------------------------
        else if (state == State.attack) {
            anim.play("attack");
            attackTimer += dt;

            // setup hitbox depending where in the attack animation we currently are
            // assumes right facing, if left facing it flips the hitbox afterwards
            // note - timer threshold values are hardcoded, they come from frame durations of each anim frame

            if (attackTimer < 0.1f) {
                attackCollider.setRect(-12, 3, 19, 12);
            }
            else if (attackTimer < 0.2f) {
                attackCollider.setRect(8, 8, 22, 12);
            }
            else if (attackTimer < 0.3f) {
                attackCollider.setRect(7, 8, 23, 12);
            }
            else if (attackTimer < 0.4f) {
                attackCollider.setRect(7, 11, 17, 9);
            }
            // done with attack anim, destroy attack collider
            else if (attackCollider != null) {
                attackCollider.destroy();
                attackCollider = null;
            }

            // flip hitbox if you're facing left
            if (facing < 0 && attackCollider != null) {
                RectI rect = attackCollider.getRect();
                rect.x = -(rect.x + rect.w);
                attackCollider.setRect(rect);
            }

            // end the attack
            if (attackTimer >= anim.animation().duration()) {
                anim.play("idle");
                state = State.normal;
            }
        }
        // HURT STATE -------------------------------------
        else if (state == State.hurt) {
            hurtTimer -= dt;
            if (hurtTimer <= 0) {
                state = State.normal;
            }

            // friction
            if (onGround) {
                mover.speed.x = Calc.approach(mover.speed.x, 0, hurt_friction * dt);
            }
        }

        // variable jumping based on how long the button is held down
        if (jumpTimer > 0) {
            jumpTimer -= dt;

            mover.speed.y = jump_impulse;

            if (!input.jump_held) {
                jumpTimer = 0;
            }
        }

        // gravity
        if (!onGround) {
            // make gravity more 'hovery' when in the air
            float grav = gravity;
            if (Calc.abs(mover.speed.y) < 20 && input.jump_held) {
                grav *= 0.4f;
            }

            mover.speed.y += grav * dt;
        }

        // invincible timer (somewhat duplicates logic from Hurtable component)
        if (invincibleTimer > 0 && state != State.hurt) {
            // flicker animation
            if (Time.on_interval(0.05f)) {
                entity().visible = !entity.visible;
            }

            invincibleTimer -= dt;
            if (invincibleTimer <= 0) {
                entity().visible = true;
            }
        }

        // hurt check (could be done with a Hurtable component)
        Collider hitbox = get(Collider.class);
        if (invincibleTimer <= 0 && hitbox.check(Mask.enemy)) {
            Time.pause_for(0.1f);
            anim.play("hurt");

            // stop attack in progress
            if (attackCollider != null) {
                attackCollider.destroy();
                attackCollider = null;
            }

            // for now bounce back is always the reverse direction player is facing
            mover.speed.set(-facing * 100, 80);

            health--;
            hurtTimer = hurt_duration;
            invincibleTimer = invincible_duration;
            state = State.hurt;
        }
    }

}
