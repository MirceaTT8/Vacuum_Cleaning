package vacworld;

import agent.Action;
import agent.Agent;
import agent.Percept;
import vacworld.actions.*;

import java.util.LinkedList;
import java.util.Queue;

public class VacAgent extends Agent {

    private boolean dirt = false;
    private boolean obstacle = false;
    private boolean bump = false;
    private boolean turnedLeft = false;
    private boolean turnedRight = false;
    private boolean wallFollowing = false;
    private boolean wentForward = true;
    private Queue<Integer> rightQueue = new LinkedList<>();
    private Queue<Integer> blockQueue = new LinkedList<>();
    private static int LEFT = 1;
    private static int RIGHT = 2;
    private static int FORWARD = 3;

    @Override
    public void see(Percept p) {
        if ( p instanceof VacPercept ){
            VacPercept vac = (VacPercept)p;

            if(vac.seeDirt())
                dirt = true;
            if(vac.seeObstacle())
                obstacle = true;
            if(vac.feelBump())
                bump = true;


        }
    }



    // Coding wall-following taking inspiration from the sniksnaks in Supaplex game
    // Every time it moves forwards, first checks if the wall is on the left, check if right is free so it can go to suck dirt

    @Override
    public Action selectAction() {
        if(!rightQueue.isEmpty()){
            if(rightQueue.poll() == LEFT)
                return new TurnLeft();
            if(rightQueue.poll() == RIGHT)
                return new TurnRight();
            if(rightQueue.poll() == FORWARD)
                return new GoForward();
        }
        if(turnedLeft && wallFollowing && turnedRight)
        {
            turnedLeft = false;
            turnedRight = false;
            fillRightQueue();
        }
        if(!turnedLeft && wallFollowing)
        {
            turnedLeft = true;
            return new TurnLeft();
        }
        if(dirt)
        {
            dirt = false;
            return new SuckDirt();
        }
        if(obstacle)
        {
            obstacle = false;
            if(!wallFollowing)
                wallFollowing = true;
            fillRightQueue();
            return new TurnRight();
        }
        wentForward = true;
        return new GoForward();
    }


//
//    @Override
//    public Action selectAction() {
//        if(!turnedLeft && wallFollowing)
//        {
//            turnedLeft = true;
//            return new TurnLeft();
//        }
//        if(dirt)
//        {
//            dirt = false;
//            return new SuckDirt();
//        }
//        if(obstacle)
//        {
//            obstacle = false;
//            if(!wallFollowing)
//                wallFollowing = true;
//            return new TurnRight();
//        }
//        turnedLeft = false;
//        return new GoForward();
//    }

    private void fillRightQueue(){
        rightQueue.add(FORWARD);
        rightQueue.add(LEFT);
        rightQueue.add(LEFT);
        rightQueue.add(FORWARD);
        rightQueue.add(RIGHT);
    }


    @Override
    public String getId() {
        return "";
    }



}
