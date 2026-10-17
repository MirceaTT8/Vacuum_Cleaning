package vacworld;

import agent.Action;
import agent.Agent;
import agent.Percept;
import vacworld.actions.GoForward;
import vacworld.actions.ShutOff;
import vacworld.actions.SuckDirt;
import vacworld.actions.TurnLeft;
import vacworld.actions.TurnRight;
import vacworld.utilities.Direction;

import java.util.*;

public class VacAgent extends Agent {
    // Immutable record provides proper value-based equals() and hashCode() for HashMap lookups
    private record Coord(int x, int y) {}

    private boolean dirt = false;
    private boolean obstacle = false;
    private boolean bump = false;

    private int curX = 0;
    private int curY = 0;
    private int curDir = Direction.NORTH;

    private static final int VISITED_WALL = 1;
    private static final int REAL_WALL = 2;

    private final Map<Coord, Integer> internalMap = new HashMap<>();
    private final Queue<Action> actionQueue = new LinkedList<>();
    private Action lastAction = null;

    public VacAgent() {
        internalMap.put(new Coord(0, 0), VISITED_WALL);
    }

    @Override
    public void see(Percept p) {
        if (p instanceof VacPercept vac) {
            this.dirt = vac.seeDirt();
            this.obstacle = vac.seeObstacle();
            this.bump = vac.feelBump();

            if (lastAction instanceof GoForward) {
                if (this.bump) {
                    int wallX = curX + Direction.DELTA_X[curDir];
                    int wallY = curY + Direction.DELTA_Y[curDir];
                    internalMap.put(new Coord(wallX, wallY), REAL_WALL);
                } else {
                    curX += Direction.DELTA_X[curDir];
                    curY += Direction.DELTA_Y[curDir];
                    internalMap.put(new Coord(curX, curY), VISITED_WALL);
                }
            } else if (lastAction instanceof TurnLeft) {
                curDir = (curDir + 3) % 4;
            } else if (lastAction instanceof TurnRight) {
                curDir = (curDir + 1) % 4;
            }

            if (this.obstacle) {
                int obsX = curX + Direction.DELTA_X[curDir];
                int obsY = curY + Direction.DELTA_Y[curDir];
                internalMap.put(new Coord(obsX, obsY), REAL_WALL);
            }
        }
    }

    @Override
    public Action selectAction() {
        // 1. Always clean dirt first
        if (dirt) {
            dirt = false;
            lastAction = new SuckDirt();
            return lastAction;
        }

        // 2. Execute queued actions, guarding against blind forward steps into newly revealed obstacles
        if (!actionQueue.isEmpty()) {
            if (actionQueue.peek() instanceof GoForward && obstacle) {
                // Cancel the queued move; see() has already marked the square ahead as REAL_WALL
                actionQueue.clear();
            } else {
                lastAction = actionQueue.poll();
                return lastAction;
            }
        }

        // 3. Wall-Riding Mode (Right -> Forward -> Left)
        int rightDir = (curDir + 1) % 4;
        int leftDir = (curDir + 3) % 4;

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

        // 4. Trapped by virtual walls: use BFS to find the nearest unvisited coordinate
        List<Action> pathToUnvisited = findNearestUnvisited();
        if (pathToUnvisited != null && !pathToUnvisited.isEmpty()) {
            actionQueue.addAll(pathToUnvisited);
            lastAction = actionQueue.poll();
            return lastAction;
        }

        // 5. No reachable unvisited squares remain
        lastAction = new ShutOff();
        return lastAction;
    }

    private boolean isUnvisited(int x, int y) {
        return !internalMap.containsKey(new Coord(x, y));
    }

    private static class SearchNode {
        int x, y, dir;
        List<Action> path;

        SearchNode(int x, int y, int dir, List<Action> path) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.path = new ArrayList<>(path);
        }
    }

    private List<Action> findNearestUnvisited() {
        Queue<SearchNode> queue = new LinkedList<>();
        Set<Coord> visitedStates = new HashSet<>();

        queue.add(new SearchNode(curX, curY, curDir, new ArrayList<>()));
        visitedStates.add(new Coord(curX, curY));

        while (!queue.isEmpty()) {
            SearchNode current = queue.poll();

            for (int d = 0; d < 4; d++) {
                int nx = current.x + Direction.DELTA_X[d];
                int ny = current.y + Direction.DELTA_Y[d];
                Coord nextCoord = new Coord(nx, ny);

                // Found an unvisited square
                if (!internalMap.containsKey(nextCoord)) {
                    List<Action> finalPath = new ArrayList<>(current.path);
                    addTurnActions(finalPath, current.dir, d);
                    finalPath.add(new GoForward());
                    return finalPath;
                }

                // Traverse through known VISITED_WALL squares (ignoring REAL_WALL)
                if (internalMap.get(nextCoord) == VISITED_WALL && !visitedStates.contains(nextCoord)) {
                    visitedStates.add(nextCoord);
                    List<Action> nextPath = new ArrayList<>(current.path);
                    addTurnActions(nextPath, current.dir, d);
                    nextPath.add(new GoForward());
                    queue.add(new SearchNode(nx, ny, d, nextPath));
                }
            }
        }
        return null;
    }

    private void addTurnActions(List<Action> path, int fromDir, int toDir) {
        int diff = (toDir - fromDir + 4) % 4;
        if (diff == 1) {
            path.add(new TurnRight());
        } else if (diff == 2) {
            path.add(new TurnRight());
            path.add(new TurnRight());
        } else if (diff == 3) {
            path.add(new TurnLeft());
        }
    }

    @Override
    public String getId() {
        return "SupaplexWallRider_v1";
    }
}