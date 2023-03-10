package bguspl.set.ex;

import bguspl.set.Env;


import javax.swing.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The id of the player (starting from 0).
     */
    public final int id;
    /**
     * The game environment object.
     */
    private final Env env;
    /**
     * Game entities.
     */
    private final Table table;
    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;
    //private final ReentrantLock lockP = new ReentrantLock();
    public AtomicBoolean setChecked = new AtomicBoolean(false);
    public AtomicBoolean state = new AtomicBoolean(false);
    public AtomicInteger ans = new AtomicInteger(0);
    /**
     * The thread representing the current player.
     */
    private Thread playerThread;
    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;
    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    /**
     * The current score of the player.
     */
    private int score;
    protected ArrayBlockingQueue nextActions;
    private Dealer dealer;
    // public int ans; // 0 is nothing, 1 is score, 2 is penalty

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.score = 0;
        nextActions = new ArrayBlockingQueue(3);
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            if (human) {
                synchronized (nextActions) {
                    while (nextActions.isEmpty() && !terminate) {
                        try {
                            nextActions.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
            try {
                addMySet();
                checkMySet();
            } catch (InterruptedException e) {
            }

        }

        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very ea smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");


            while (!terminate) {
                Random rn = new Random();
                int x = rn.nextInt(env.config.tableSize);  //random a slot between 0-11
                keyPressed(x);
                synchronized (aiThread) {
                    while (nextActions.size() == 3 && !terminate) {
                        try {
                            aiThread.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

            }

            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        try {
            Thread.currentThread().interrupt();
            Thread.currentThread().join();
        } catch (InterruptedException e) {
        }
    }


    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (human)
            synchronized (nextActions) {
                nextActions.notifyAll();
            }
        if (nextActions.size() < 3 && table.tableLock.get()) {
            nextActions.add(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public synchronized void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score++;
        env.ui.setScore(id, score);
        long startTime = System.currentTimeMillis();
        long delta = env.config.pointFreezeMillis - (System.currentTimeMillis() - startTime);
        while (delta >= 0) {
            delta = env.config.pointFreezeMillis - (System.currentTimeMillis() - startTime);
            env.ui.setFreeze(id, delta + 1000);
        }
        env.ui.setFreeze(id, 0);
        ans.set(0);
        setChecked.set(false);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {
        long startTime = System.currentTimeMillis();
        long delta = env.config.penaltyFreezeMillis - (System.currentTimeMillis() - startTime);
        while (delta >= 1) {
            delta = env.config.penaltyFreezeMillis - (System.currentTimeMillis() - startTime);
            env.ui.setFreeze(id, delta + 1000);
        }
        env.ui.setFreeze(id, 0);
        ans.set(0);
        setChecked.set(true);

    }

    public void cleanActions() {
        nextActions.clear();
        setChecked.set(false);
        if (!human)
            synchronized (aiThread) {
                aiThread.notifyAll();
            }
        synchronized (this) {
            this.notifyAll();
        }
        state.set(true);
    }

    public Thread getPlayerThread() {
        return playerThread;
    }

    public int score() {
        return score;
    }

    public synchronized void addMySet() throws InterruptedException {
        if (!nextActions.isEmpty() && table.tableLock.get() && state.get()) { // editing the set
            int action = (int) nextActions.take();
            if (!human)
                synchronized (aiThread) {
                    aiThread.notifyAll();
                }
            if (table.slotToCard[action] != null && table.slotToCard[action] != -1) {
                if (table.removeToken(id, action)) {  // remove token
                    setChecked.set(false);
                } else                                        // add token
                    table.placeToken(id, action);
            }
        }
    }


    public void checkMySet() throws InterruptedException {
        if (state.get() && table.tableLock.get()) {
            int[] currSet = new int[4];
            Arrays.setAll(currSet, i -> table.getTokens(id)[i]);
            if (currSet[0] != -1 && currSet[1] != -1 && currSet[2] != -1 && !setChecked.get()) //complete a possible set
            {
                state.set(false);
                setChecked.set(true);
                dealer.pushSet(currSet);  // let the dealer to check your set
                synchronized (this) {
                    while (!state.get())
                        wait();
                }
            }
            if (ans.get() == 1) {
                point();

            } else if (ans.get() == 2) {
                penalty();
            }
        }
    }
}



