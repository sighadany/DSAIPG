/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.projects.mcts.tictactoe;

import com.phasmidsoftware.dsaipg.projects.mcts.core.Move;
import com.phasmidsoftware.dsaipg.projects.mcts.core.Node;
import com.phasmidsoftware.dsaipg.projects.mcts.core.State;

import java.util.*;

/**
 * Class to represent a Monte Carlo Tree Search for TicTacToe.
 */
public class MCTS {

    public static void main(String[] args) {
        TicTacToe game = new TicTacToe();
        State<TicTacToe> currentState = game.start();

        // Create MCTS players with different random generators
        MCTS mctsPlayerX = new MCTS(new TicTacToeNode(currentState, null), new Random(System.nanoTime()));
        // Wait a bit to ensure a different seed
        try { Thread.sleep(1); } catch (InterruptedException e) { }
        MCTS mctsPlayerO = new MCTS(new TicTacToeNode(currentState, null), new Random(System.nanoTime()));

        // Set search parameters (may differ between players)
        double searchTimeX = 1.0; // seconds
        double searchTimeO = 1.0; // seconds

        System.out.println("Starting TicTacToe match between two MCTS players");
        System.out.println("Initial state:\n" + currentState);

        int moveCount = 0;


        // Game loop
        while (!currentState.isTerminal()) {
            moveCount++;
            int currentPlayer = currentState.player();
            System.out.println("\nMove #" + moveCount + " - Player " +
                    (currentPlayer == TicTacToe.X ? "X" : "O") + " thinking...");

            // Let the appropriate MCTS player choose the next move
            Node<TicTacToe> nextNode;
            long startTime = System.currentTimeMillis();

            if (currentPlayer == TicTacToe.X) {
                nextNode = mctsPlayerX.search(new TicTacToeNode(currentState, null), searchTimeX);

//                mctsPlayerX.search(new TicTacToeNode(currentState, null), searchTimeX);
                long timeSpent = System.currentTimeMillis() - startTime;
                System.out.println("Player X completed search in " + (timeSpent/1000.0) + " seconds");
            } else {
                nextNode = mctsPlayerO.search(new TicTacToeNode(currentState, null), searchTimeO);

//                mctsPlayerO.search(new TicTacToeNode(currentState, null), searchTimeO);
                long timeSpent = System.currentTimeMillis() - startTime;
                System.out.println("Player O completed search in " + (timeSpent/1000.0) + " seconds");
            }
//
            // Update the game state
            currentState = nextNode.state();

            // Display the current board
            System.out.println("Player " + (currentPlayer == TicTacToe.X ? "X" : "O") + " moved to:");
            System.out.println(currentState);
        }
//
//        // Report the game result
        Optional<Integer> winner = currentState.winner();
        if (winner.isPresent()) {
            int winningPlayer = winner.get();
            System.out.println("\nGame over! Player " +
                    (winningPlayer == TicTacToe.X ? "X" : "O") + " wins!");
        } else {
            System.out.println("\nGame over! It's a draw!");
        }

        // Display final statistics
        System.out.println("Total moves: " + moveCount);
    }

    public MCTS(Node<TicTacToe> root, Random random) {
        this.root = root;
        this.random = random;
    }

    public Node<TicTacToe> search(Node<TicTacToe> root) {
//    public void search(Node<TicTacToe> root) {
        return search(root, 5.0);
//        search(root, 5.0);
    }

    public Node<TicTacToe> search(Node<TicTacToe> root, double maxTime) {
//    public void search(Node<TicTacToe> root, double maxTime) {
        long startTime = System.currentTimeMillis();
        long timeLimit = (long)(maxTime * 1000);
//        int iterations = 0;

        while (System.currentTimeMillis() - startTime < timeLimit) {

            // 1. Selection: Find a promising node to expand
            Node<TicTacToe> promisingNode = selectPromisingNode(root);

            // 2. Expansion: If not terminal, expand one child (or ensure explored)
            Node<TicTacToe> nodeToExplore = promisingNode;
            if (!promisingNode.isLeaf()) {
                // Ensure children are created if they weren't already
                if (promisingNode.children().isEmpty()) {
                    promisingNode.explore(); // Creates all children
                }
                // If explore creates all children, we might want to pick one to simulate.
                // If it's not fully expanded, expandNode selects one untried.
                if (!isFullyExpanded(promisingNode)) {
                    nodeToExplore = expandNode(promisingNode); // Selects or creates ONE node to simulate
                } else {
                    // If fully expanded but not a leaf, UCT already selected the child to explore further
                    nodeToExplore = findBestNodeUct(promisingNode);
                    // Ensure we don't get stuck if findBestNodeUct returns null (shouldn't happen)
                    if (nodeToExplore == null) nodeToExplore = promisingNode;
                }

            }

            // 3. Simulation: Play out from the selected/expanded node
            double playoutResult = simulateRandomPlayout(nodeToExplore); // Returns 1.0 for X win, 0.0 for O win, 0.5 for Draw


            // 4. Backpropagation: Update nodes on the path
            backPropagate(nodeToExplore, playoutResult);

        }

        return bestChild(root);

    }


    private Node<TicTacToe> selectPromisingNode(Node<TicTacToe> startNode) {
        // Add this before calling node.explore()

        Node<TicTacToe> node = startNode;
        while (!node.isLeaf()) {
            // Ensure children are available
            if (node.children().isEmpty()) {
                node.explore();
                // If after explore() there are still no children but it's not a leaf,
                // something is wrong with the state representation
                if (node.children().isEmpty()) {
                    System.err.println("Error: after explore() there are still no children!");
                    // This is a node that should have children (not a leaf) but doesn't
                    // Return it so we can expand it manually
                    return node;
                }
            }

            if (!isFullyExpanded(node)) {
                // Node has unexplored moves, return it for expansion
                return node;
            } else {
                // Node is fully expanded, choose best child via UCT
                Node<TicTacToe> bestChild = findBestNodeUct(node);
                if (bestChild == null) {
                    // This shouldn't happen in a properly functioning tree,
                    // but return current node to avoid crashes
                    System.err.println("Error: findBestNodeUct returned null from a supposedly fully expanded node!");
                    return node;
                }
                node = bestChild;
            }
        }
        return node; // Return leaf node
    }


    public boolean isFullyExpanded(Node<TicTacToe> node) {
        if(node.children().isEmpty()) {
            return false;
        }else {
            for(Node<TicTacToe> childNode : node.children()) {
                if(childNode.visits() < 1) {
                    return false;
                }
            }
        }
        return true;
    }



    private Node<TicTacToe> findBestNodeUct(Node<TicTacToe> node) {
        Node<TicTacToe> selected = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        double explorationConstant = 1.414; // sqrt(2)

        int parentVisits = node.visits();
        if (parentVisits == 0) {
            parentVisits = 1;
        }

        Collection<Node<TicTacToe>> children = node.children();
        if (children.isEmpty()){
            System.err.println("Warning: findBestNodeUct called on a node with no children.");
            return null;
        }

        // Increase randomness for early tree exploration
        double randomFactor = 0.1; // Larger value = more exploration

        for (Node<TicTacToe> child : children) {
            int childVisits = child.visits();

            if (childVisits == 0) {
                return child;
            }

            // UCT Formula with significantly more randomization
            double winScore = child.wins();
            double exploitationTerm = winScore / (double) childVisits;
            double explorationTerm = explorationConstant * Math.sqrt(Math.log(parentVisits) / (double) childVisits);

            // Add a stronger random component to promote diversity in move selection
            double uctValue = exploitationTerm + explorationTerm + (random.nextDouble() * randomFactor);

            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }

        if (selected == null && !children.isEmpty()) {
            return children.iterator().next();
        }

        return selected;
    }

//    private Node<TicTacToe> findBestNodeUct(Node<TicTacToe> node) {
//
//        Node<TicTacToe> selected = null;
//        double bestValue = Double.NEGATIVE_INFINITY;
//        double explorationConstant = 1.414; // sqrt(2)
//
//        int parentVisits = node.visits(); // Read via getter
//
//        // Ensure parent playouts is at least 1 for log calculation
//        if (parentVisits == 0) {
//            // This can happen if we call UCT on a node immediately after creation
//            // before backpropagation reaches it. Assign a default value or handle differently.
//            // System.err.println("Warning: UCT called on node with 0 playouts. Using 1.");
//            parentVisits = 1; // Avoid math errors, implies minimal exploration info
//        }
//
//
//        Collection<Node<TicTacToe>> children = node.children(); // Get children
//        if (children.isEmpty()){
//            System.err.println("Warning: findBestNodeUct called on a node with no children.");
//            return null; // Cannot select from empty set
//        }
//
//
//        for (Node<TicTacToe> child : children) {
//            int childVisits = child.visits(); // Read via getter
//
//            if (childVisits == 0) {
//                // Assign a very high value to encourage exploration of unvisited nodes
//                // This is crucial if expandNode doesn't guarantee returning a 0-playout node
//                // Or return immediately if found (common MCTS strategy)
//                // System.out.println("Selecting unvisited child: " + child.state());
//                return child;
//            }
//
//            // UCT Formula
//            // Assumes child.wins() returns the accumulated score (2/1/0)
//            double winScore = child.wins(); // Read via getter
//            double exploitationTerm = winScore / (double) childVisits; // Average score
//
//            double explorationTerm = explorationConstant * Math.sqrt(Math.log(parentVisits) / (double) childVisits);
//
//            double uctValue = exploitationTerm + explorationTerm;
//            uctValue += random.nextDouble() * 0.0001; // Tie-breaking noise
//
//            // System.out.println(" Child: " + child.state() + ", Score: " + winScore + ", Playouts: " + childPlayouts + ", UCT: " + uctValue); // Debug
//
//            if (uctValue > bestValue) {
//                selected = child;
//                bestValue = uctValue;
//            }
//        }
//
//        if (selected == null) {
//            System.err.println("Warning: UCT failed to select a child from non-empty list. Returning first child.");
//            if (!children.isEmpty()) return children.iterator().next(); // Fallback
//        }
//
//        return selected;
//    }


    /**
     * Selects an unsimulated child node to explore from.
     * Assumes `explore()` has already been called if necessary to create children.
     * If all children have been simulated, it might return null or delegate to UCT.
     * Here, we assume selection phase identified a node that *can* be expanded further.
     * It finds one child that hasn't been visited (visits=0) or creates one if not all exist.
     */
    private Node<TicTacToe> expandNode(Node<TicTacToe> node) {
        if (node.isLeaf()) return node; // Cannot expand terminal node

        // First, check for existing children with 0 playouts
        for (Node<TicTacToe> child : node.children()) {
            if (child.visits() == 0) {
                return child; // Found an existing but unvisited child
            }
        }

        // If all existing children have visits > 0, but not all moves were made children yet
        // (This case might indicate explore() wasn't called or didn't finish)
        // Let's rely on selectPromisingNode identifying the right node and UCT picking unvisited ones.
        // This method could simply pick a random child if called unexpectedly on a fully simulated node.
        if (!node.children().isEmpty()) {
            System.err.println("Warning: expandNode called but no child with 0 playouts found. Returning first child.");
            return node.children().iterator().next();
        } else {
            System.err.println("Error: expandNode called on node with no children after exploration?");
            return node;
        }

        // Alternative: Explicitly find untried moves (like in previous example)
        // This requires comparing states which can be inefficient.
        // Relying on playouts=0 is more common.
    }

    // Performs simulation and returns result
//    private double simulateRandomPlayout(Node<TicTacToe> node) {
//        // Use the state from the node where simulation starts
//        State<TicTacToe> tempState = node.state();
//
//        // Simulate until the game ends
//        while (!tempState.isTerminal()) {
//            // Get possible next states using the State's random move selection
//            int currentPlayer = tempState.player();
//            Collection<Move<TicTacToe>> moves = tempState.moves(currentPlayer); // Use State interface method
//            if (moves.isEmpty()) {
//                System.err.println("Error: No moves available from non-terminal state?");
//                break; // Avoid infinite loop
//            }
//            Move<TicTacToe> move = tempState.chooseMove(currentPlayer);
//            tempState = tempState.next(move);
//        }
//
//        node.addVisit();
//
//        // Determine the result from the terminal state
//        Optional<Integer> winnerOpt = tempState.winner();
//        if (winnerOpt.isPresent()) {
//            // Return 1.0 if winner is Player 1 (X, opener), 0.0 if Player 0 (O), 0.5 if it's a draw
//            int winner = winnerOpt.get();
//            int opener = tempState.game().opener(); // Get opener ID (e.g., 1)
//            return (winner == opener) ? 1.0 : 0.0;
//        } else {
//            // It's a draw
//            return 0.5;
//        }
//    }


    private double simulateRandomPlayout(Node<TicTacToe> node) {
        State<TicTacToe> tempState = node.state();
        int moveCount = 0;

        while (!tempState.isTerminal()) {
            moveCount++;

            int currentPlayer = tempState.player();
            Collection<Move<TicTacToe>> moves = tempState.moves(currentPlayer);

            if (moves.isEmpty()) {
                System.err.println("ERROR: No moves available in non-terminal state!");
                break;
            }

            // Select a truly random move instead of using chooseMove()
            List<Move<TicTacToe>> moveList = new ArrayList<>(moves);
            Move<TicTacToe> move = moveList.get(random.nextInt(moveList.size()));

            tempState = tempState.next(move);
        }

        node.addVisit();

        Optional<Integer> winnerOpt = tempState.winner();
        if (winnerOpt.isPresent()) {
            int winner = winnerOpt.get();
            int opener = tempState.game().opener();
            return (winner == opener) ? 1.0 : 0.0;
        } else {
            return 0.5;
        }
    }


//    private double simulateRandomPlayout(Node<TicTacToe> node) {
//        State<TicTacToe> tempState = node.state();
////        System.out.println("Starting simulation from state: " + tempState);
//
//        int moveCount = 0;
//        while (!tempState.isTerminal()) {
//            moveCount++;
////            System.out.println("\n\n\n\nSimulation move #" + moveCount);
//
//            int currentPlayer = tempState.player();
////            System.out.println("\nSimulating move for player: " + currentPlayer);
////            System.out.println("\nCurrent state: " + tempState);
//
//            // Debug the Position instance
//            TicTacToe.TicTacToeState tstate = (TicTacToe.TicTacToeState) tempState;
//            Position pos = tstate.position();
////            System.out.println("Board count: " + pos.getCount() + ", Full: " + pos.full());
//
//            Collection<Move<TicTacToe>> moves = tempState.moves(currentPlayer);
////            System.out.println("Available moves: " + moves.size());
//
//            if (moves.isEmpty()) {
//                System.err.println("ERROR: No moves available in non-terminal state!");
//                // Check if board is actually full
//                System.out.println("Board appears to be full but isTerminal() returned false");
//                break;
//            }
//
//            Move<TicTacToe> move = tempState.chooseMove(currentPlayer);
//            tempState = tempState.next(move);
////            System.out.println("After move, new state: " + tempState);
//
//            // Debug terminal state again
////            System.out.println("Is terminal after move? " + tempState.isTerminal());
//            tstate = (TicTacToe.TicTacToeState) tempState;
//            pos = tstate.position();
////            System.out.println("Board count after move: " + pos.getCount() + ", Full: " + pos.full());
//        }
//
////        System.out.println("Simulation ended after " + moveCount + " moves");
////        System.out.println("Final state: " + tempState);
////        System.out.println("Is terminal: " + tempState.isTerminal());
////        Optional<Integer> winner = tempState.winner();
////        System.out.println("Winner: " + (winner.isPresent() ? winner.get() : "none (draw)"));
//
//        node.addVisit();
//
//        // Determine the result from the terminal state
//        Optional<Integer> winnerOpt = tempState.winner();
//        if (winnerOpt.isPresent()) {
//            // Return 1.0 if winner is Player 1 (X, opener), 0.0 if Player 0 (O), 0.5 if it's a draw
//            int winner1 = winnerOpt.get();
//            int opener = tempState.game().opener(); // Get opener ID (e.g., 1)
//            return (winner1 == opener) ? 1.0 : 0.0;
//        } else {
//            // It's a draw
//            return 0.5;
//        }
//    }


    /**
     * Backpropagates the simulation result up the tree from the startNode.
     * This method now contains the logic to calculate the score adjustment
     * and directly modifies the wins/playouts fields of TicTacToeNode.
     *
     * @param startNode        The node from which the simulation started.
     * @param simulationResult The result (1.0=X win, 0.0=O win, 0.5=Draw).
     */
    private void backPropagate(Node<TicTacToe> startNode, double simulationResult) {
        // Get the depth of this node
//        int nodeDepth = getNodeDepth(startNode);
//        maxDepthReached = Math.max(maxDepthReached, nodeDepth);

        Node<TicTacToe> tempNode = startNode;
        while (tempNode != null) {
            if (tempNode instanceof TicTacToeNode) {
                TicTacToeNode tttNode = (TicTacToeNode) tempNode;
                tttNode.update(simulationResult);
            } else {
                System.err.println("Error: Node in backpropagation path is not a TicTacToeNode!");
                break;
            }
            tempNode = tempNode.getParent(); // Move up the tree
        }
    }



    public Node<TicTacToe> bestChild(Node<TicTacToe> node) {
        // For the actual move selection, we'll use a temperature-based approach
        // to balance between exploitation and exploration

        Collection<Node<TicTacToe>> children = node.children();
        if (children.isEmpty()) {
            System.err.println("Error: Cannot select best child from node with no children.");
            return null;
        }

        // First, find the node with the most visits as the baseline
        Node<TicTacToe> mostVisitedNode = null;
        int maxVisits = -1;

        for (Node<TicTacToe> child : children) {
            int childVisits = child.visits();
            if (childVisits > maxVisits) {
                mostVisitedNode = child;
                maxVisits = childVisits;
            }
        }

        // If we're early in the game (first 2-3 moves), add some randomness
        // This encourages opening variety
        int moveCount = 0;
        Position position = ((TicTacToe.TicTacToeState)node.state()).position();
        moveCount = position.getCount();

        if (moveCount < 3 && random.nextDouble() < 0.5) {
            // 50% chance to pick a random move in the first 3 moves
            List<Node<TicTacToe>> childList = new ArrayList<>(children);
            return childList.get(random.nextInt(childList.size()));
        }

        // Otherwise, select based on a probability distribution weighted by visit counts
        // Nodes with higher visit counts are more likely to be chosen
        if (children.size() > 1 && random.nextDouble() < 0.2) {  // 20% chance to use probabilistic selection
            double totalVisits = 0;
            for (Node<TicTacToe> child : children) {
                totalVisits += child.visits();
            }

            double rnd = random.nextDouble() * totalVisits;
            double sum = 0;

            for (Node<TicTacToe> child : children) {
                sum += child.visits();
                if (sum >= rnd) {
                    return child;
                }
            }
        }

        // Default: return the most visited node (greedy selection)
        return mostVisitedNode;
    }


    private final Node<TicTacToe> root;
    private final Random random;
    private int maxDepthReached = 0;
}