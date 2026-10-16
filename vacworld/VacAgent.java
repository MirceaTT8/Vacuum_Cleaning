package vacworld;

import agent.Action;
import agent.Agent;
import agent.Percept;
import vacworld.actions.*;
import vacworld.utilities.Direction;

import java.util.*;

public class VacAgent extends Agent {

    private static final int VISITED_WALL = 1; // Visited cells become new virtual walls
    private static final int REAL_WALL    = 2; // Physical walls / obstacles

    private final Map<Square, Integer> internalMap = new HashMap<>();

    private int curX = 0;
    private int curY = 0;
    private int curDir = Direction.NORTH;

    private boolean dirt = false;
    private boolean obstacle = false;
    private boolean bump = false;

    private final Queue<Action> actionQueue = new LinkedList<>();
    private Action lastAction = null;

    public VacAgent() {
        internalMap.put(new Square(curX, curY), VISITED_WALL);
    }


    @Override
    public void see(Percept p) {
        if (p instanceof VacPercept vac) {
            dirt = vac.seeDirt();
            obstacle = vac.seeObstacle();
            bump = vac.feelBump();
        }

        if (lastAction != null) {
            if (lastAction instanceof TurnRight) {
                curDir = (curDir + 1) % 4;
            } else if (lastAction instanceof TurnLeft) {
                curDir = (curDir + 3) % 4;
            } else if (lastAction instanceof GoForward) {
                if (bump) {
                    int bx = curX + Direction.DELTA_X[curDir];
                    int by = curY + Direction.DELTA_Y[curDir];
                    internalMap.put(new Square(bx, by), REAL_WALL);
                } else {
                    curX += Direction.DELTA_X[curDir];
                    curY += Direction.DELTA_Y[curDir];
                    // Visited coordinate becomes a new wall for wall-riding
                    internalMap.put(new Square(curX, curY), VISITED_WALL);
                }
            }
        }

        if (obstacle) {
            int fx = curX + Direction.DELTA_X[curDir];
            int fy = curY + Direction.DELTA_Y[curDir];
            internalMap.put(new Square(fx, fy), REAL_WALL);
        }
    }



    // Coding wall-following taking inspiration from the sniksnaks in Supaplex game
    // Every time it moves forwards, first checks if the wall is on the left, check if right is free so it can go to suck dirt

    @Override
    public Action selectAction() {
        // 1. Always suck dirt first
        if (dirt) {
            dirt = false;
            lastAction = new SuckDirt();
            return lastAction;
        }

        // 2. Execute any queued turn+move actions
        if (!actionQueue.isEmpty()) {
            lastAction = actionQueue.poll();
            return lastAction;
        }

        // 3. Wall-Riding (Right-hand priority: Right -> Forward -> Left)
        // Since visited squares are marked in internalMap, the robot rides along them as walls
        int rightDir = (curDir + 1) % 4;
        int leftDir  = (curDir + 3) % 4;

        if (isUnvisited(curX + Direction.DELTA_X[rightDir], curY + Direction.DELTA_Y[rightDir])) {
            actionQueue.add(new GoForward());
            lastAction = new TurnRight();
            return lastAction;
        }

        if (isUnvisited(curX + Direction.DELTA_X[curDir], curY + Direction.DELTA_Y[curDir])) {
            lastAction = new GoForward();
            return lastAction;
        }

        if (isUnvisited(curX + Direction.DELTA_X[leftDir], curY + Direction.DELTA_Y[leftDir])) {
            actionQueue.add(new GoForward());
            lastAction = new TurnLeft();
            return lastAction;
        }

        // 4. If boxed in by virtual/real walls, step over visited squares to the nearest unvisited spot
        List<Action> path = findNearestUnvisited();
        if (path != null && !path.isEmpty()) {
            actionQueue.addAll(path);
            lastAction = actionQueue.poll();
            return lastAction;
        }

        // 5. All reachable squares have been visited and cleaned -> ShutOff
        lastAction = new ShutOff();
        return lastAction;
    }


    private boolean isUnvisited(int x, int y) {
        return !internalMap.containsKey(new Square(x, y));
    }

    private List<Action> findNearestUnvisited() {
        Queue<SearchNode> queue = new LinkedList<>();
        Set<String> visitedStates = new HashSet<>();

        queue.add(new SearchNode(curX, curY, curDir, new ArrayList<>()));
        visitedStates.add(curX + "," + curY + "," + curDir);

        while (!queue.isEmpty()) {
            SearchNode curr = queue.poll();

            // Check if any neighbor of curr is unvisited
            for (int d = 0; d < 4; d++) {
                int nx = curr.x + Direction.DELTA_X[d];
                int ny = curr.y + Direction.DELTA_Y[d];
                if (isUnvisited(nx, ny)) {
                    List<Action> plan = new ArrayList<>(curr.actions);
                    alignAndStep(curr.dir, d, plan);
                    return plan;
                }
            }

            // Turn Left
            int lDir = (curr.dir + 3) % 4;
            String lKey = curr.x + "," + curr.y + "," + lDir;
            if (visitedStates.add(lKey)) {
                List<Action> next = new ArrayList<>(curr.actions);
                next.add(new TurnLeft());
                queue.add(new SearchNode(curr.x, curr.y, lDir, next));
            }

            // Turn Right
            int rDir = (curr.dir + 1) % 4;
            String rKey = curr.x + "," + curr.y + "," + rDir;
            if (visitedStates.add(rKey)) {
                List<Action> next = new ArrayList<>(curr.actions);
                next.add(new TurnRight());
                queue.add(new SearchNode(curr.x, curr.y, rDir, next));
            }

            // Move Forward across already-visited squares
            int fx = curr.x + Direction.DELTA_X[curr.dir];
            int fy = curr.y + Direction.DELTA_Y[curr.dir];
            Integer cellType = internalMap.get(new Square(fx, fy));
            String fKey = fx + "," + fy + "," + curr.dir;
            if (cellType != null && cellType == VISITED_WALL && visitedStates.add(fKey)) {
                List<Action> next = new ArrayList<>(curr.actions);
                next.add(new GoForward());
                queue.add(new SearchNode(fx, fy, curr.dir, next));
            }
        }
        return null;
    }

    private void alignAndStep(int fromDir, int toDir, List<Action> plan) {
        int diff = (toDir - fromDir + 4) % 4;
        if (diff == 1) {
            plan.add(new TurnRight());
        } else if (diff == 2) {
            plan.add(new TurnRight());
            plan.add(new TurnRight());
        } else if (diff == 3) {
            plan.add(new TurnLeft());
        }
        plan.add(new GoForward());
    }

    @Override
    public String getId() {
        return "";
    }

    private static class SearchNode {
        int x, y, dir;
        List<Action> actions;

        SearchNode(int x, int y, int dir, List<Action> actions) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.actions = actions;
        }
    }



}
