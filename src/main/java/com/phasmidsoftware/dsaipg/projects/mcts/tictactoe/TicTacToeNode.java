/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.projects.mcts.tictactoe;

import com.phasmidsoftware.dsaipg.projects.mcts.core.Move;
import com.phasmidsoftware.dsaipg.projects.mcts.core.Node;
import com.phasmidsoftware.dsaipg.projects.mcts.core.State;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class TicTacToeNode implements Node<TicTacToe> {

    /**
     * @return true if this node is a leaf node (in which case no further exploration is possible).
     */
    public boolean isLeaf() {
        return state().isTerminal();
    }

    /**
     * @return the State of the Game G that this Node represents.
     */
    public State<TicTacToe> state() {
        return state;
    }

    /**
     * Method to determine if the player who plays to this node is the opening player (by analogy with chess).
     * For this method, we assume that X goes first so is "white."
     * NOTE: this assumes a two-player game.
     *
     * @return true if this node represents a "white" move; false for "black."
     */
    public boolean white() {
        return state.player() == state.game().opener();
    }

    /**
     * @return the children of this Node.
     */
    public Collection<Node<TicTacToe>> children() {
        return children;
    }

    /**
     * Method to add a child to this Node.
     *
     * @param state the State for the new chile.
     */
    public void addChild(State<TicTacToe> state) {
        Node<TicTacToe> newChild = new TicTacToeNode(state, this);
        this.children.add(newChild);
    }

    /**
     * @return the score for this Node and its descendents a win is worth 2 points, a draw is worth 1 point.
     */
    public int wins() {
        return wins;
    }

    /**
     * @return the number of playouts evaluated (including this node). A leaf node will have a playouts value of 1.
     */
    public int playouts() {
        return playouts;
    }

    /**
     * @return the number of visits evaluated (including this node). A leaf node will have a visits value of 1.
     */
    public int visits() {
        return visits;
    }

    /**
     * @return add a new visit.
     */
    public void addVisit() {
        visits++;
    }

    /**
     * Gets the parent of this node.
     *
     * @return The parent node, or null if this is the root node.
     */
    public Node<TicTacToe> getParent() {
        return parent;
    }

    @Override
    public void explore() {
        if (isLeaf()) return;
        if (children().isEmpty()) {
            State<TicTacToe> currentState = state();
            int currentPlayer = currentState.player();

            try {
                // Try to get moves using the normal way
                Collection<Move<TicTacToe>> possibleMoves = currentState.moves(currentPlayer);

                // Create child nodes for each move
                for (Move<TicTacToe> move : possibleMoves) {
                    State<TicTacToe> nextState = currentState.next(move);
                    addChild(nextState);
                }

//                System.out.println("Added " + children().size() + " children to node");
            } catch (RuntimeException e) {
                // If we get an exception about consecutive moves, this is likely
                // happening during simulation when we're projecting future states
                System.err.println("Explore caught exception: " + e.getMessage());

                // This is a workaround for the simulation phase
                if (e.getMessage().contains("consecutive moves by same player")) {
                    System.out.println("Working around consecutive move issue in explore");
                    // Use the other player instead
                    int otherPlayer = 1 - currentPlayer; // Switch between 0 and 1
                    try {
                        Collection<Move<TicTacToe>> possibleMoves = currentState.moves(otherPlayer);
                        for (Move<TicTacToe> move : possibleMoves) {
                            State<TicTacToe> nextState = currentState.next(move);
                            addChild(nextState);
                        }
                        System.out.println("Added " + children().size() + " children using other player");
                    } catch (Exception e2) {
                        System.err.println("Failed with other player too: " + e2.getMessage());
                    }
                }
            }
        }
    }


    /**
     * Updates this node's statistics based on the result of a *single* simulation
     * that passed through this node. This is the core of MCTS backpropagation.
     *
     * @param simulationResult The result of the simulation. Convention used here:
     * 1.0 = Player 1 (X, the opener) won.
     * 0.0 = Player 0 (O) won.
     * 0.5 = Draw.
     */
    public void update(double simulationResult) {
        this.playouts++; // Always increment playout count

        // Determine the score to add based on the result and whose turn it was.
        // We want to increment 'wins' if the result was favorable for the player
        // who made the move *to* this state.
        int playerWhoseTurnIsNext = state.player();
        int opener = state.game().opener(); // Assume opener is 1 (X)
        int otherPlayer = (opener == 1) ? 0 : 1; // Assume other player is 0 (O)

        // Player who moved TO this state is the opponent of playerWhoseTurnIsNext
        int playerWhoMovedToThisState = (playerWhoseTurnIsNext == opener) ? otherPlayer : opener;

        int scoreToAdd = 0; // Use 2 for win, 1 for draw, 0 for loss scale

        if (simulationResult == 0.5) { // Draw
            scoreToAdd = 1;
        } else if (simulationResult == 1.0) { // X (Opener) won the simulation
            if (playerWhoMovedToThisState == opener) {
                scoreToAdd = 2; // Favorable outcome for the player who moved here
            } else {
                scoreToAdd = 0; // Unfavorable outcome
            }
        } else if (simulationResult == 0.0) { // O (Other player) won the simulation
            if (playerWhoMovedToThisState == otherPlayer) {
                scoreToAdd = 2; // Favorable outcome for the player who moved here
            } else {
                scoreToAdd = 0; // Unfavorable outcome
            }
        }

        this.wins += scoreToAdd;
    }



    public TicTacToeNode(State<TicTacToe> state, Node<TicTacToe> parent) {
        this.state = state;
        this.parent = parent; // Set the parent
        children = new ArrayList<>();
        this.wins = 0;
        this.playouts = 0;
        this.visits = 0;
    }

    private final State<TicTacToe> state;
    private final ArrayList<Node<TicTacToe>> children;
    private final Node<TicTacToe> parent; // Added: reference to parent node

    private int wins;
    private int playouts;
    private int visits;
}