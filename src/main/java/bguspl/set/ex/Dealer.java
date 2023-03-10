package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.shuffle;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;
    protected Semaphore sem;
    ArrayBlockingQueue setsTocheck;
    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private long startTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        sem = new Semaphore(1, true);
        setsTocheck = new ArrayBlockingQueue<int[]>(players.length, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        startPlayers();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }

    }


    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        Thread.currentThread().interrupt();
        terminate = true;
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }


    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (!setsTocheck.isEmpty()) {
            int[] cardArr = (int[]) setsTocheck.poll();
            int idPlayer = cardArr[3]; // Spliting cardArr to currSet card and id player
            int[] currSet = new int[3];
            for (int i = 0; i < cardArr.length - 1; i++)
                currSet[i] = cardArr[i];
            int setKind = setKind(currSet);

            if (setKind == 1) // in case correct set arranging table
            {
                table.tableLock.set(false);
                table.removeSet(currSet);
            }
            players[idPlayer].ans.set(setKind); //let the player know if his set is legal
            players[idPlayer].state.set(true);
            synchronized (players[idPlayer]) {
                players[idPlayer].notifyAll();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.tableLock.set(false);
        List<Integer> spots = new ArrayList<>();
        for (int i = 0; i < table.freeSpots.length; i++) {
            if (table.freeSpots[i] == 0) {
                spots.add(i);
                table.freeSpots[i] = 1;
            }
        }
        if (spots.size() > 0) {
            shuffle(deck);
            shuffle(spots);
            while (deck.size() > 0 && !spots.isEmpty())
                table.placeCard(deck.remove(deck.size() - 1), spots.remove(spots.size() - 1));
            updateTimerDisplay(true);
        }

        table.tableLock.set(true);
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        if(setsTocheck.isEmpty()) {
            long delta = env.config.turnTimeoutMillis - (System.currentTimeMillis() - startTime);
            if (delta <= env.config.turnTimeoutWarningMillis)
                try {
                    wait(10);
                } catch (InterruptedException e) {
                }
            else {
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */

    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            startTime = System.currentTimeMillis();
            reshuffleTime = startTime + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis+999, false);
        } else {
            long delta = env.config.turnTimeoutMillis - (System.currentTimeMillis() - startTime);
            if (delta > env.config.turnTimeoutWarningMillis)
                env.ui.setCountdown(delta+999, false);
            else {
                if (delta < 0)
                    delta = 0;
                env.ui.setCountdown(delta, true);
            }
        }
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        table.tableLock.set(false);   // lock table so no player can place/remove tokens while removing the cards
        table.removeAllTokens(players.length);
            setsTocheck.clear();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null) {
                int card = table.slotToCard[i];
                table.removeCard(i);
                deck.add(card);
                env.ui.removeCard(i);
            }
        }
        for (Player p : players) {
            p.cleanActions();       ///initialize player for the next round
        }
    }


    /**
     * Check who is/are the winner/s and displays them.
     *
     * @return
     */
    protected int announceWinners() {
        List<Integer> winners = new LinkedList<Integer>();
        int highestScore = 0;
        for (Player p : players) {
            if (p.score() > highestScore) {
                highestScore = p.score();
            }
        }
        for (Player p : players) {
            if (p.score() == highestScore) {
                winners.add(p.id);
            }
        }
        int[] ans = new int[winners.size()];
        for (int i = 0; i < ans.length; i++)
            ans[i] = winners.remove(winners.size() - 1);
        env.ui.announceWinner(ans);
        return ans[0];
    }

    public void pushSet(int[] slots) throws InterruptedException {   // adding sets to the queue and we use semaphore to ensure fairness
        sem.acquire();
        long leftTime = env.config.turnTimeoutMillis - (System.currentTimeMillis() - startTime);
        if (leftTime > 0) {
            int[] cards = new int[slots.length];
            for (int i = 0; i < 3; i++) {
                cards[i] = table.slotToCard[slots[i]];
            }
            cards[cards.length - 1] = slots[slots.length - 1];
            setsTocheck.offer(cards);
            synchronized (this) {
                this.notifyAll();
            }
        }
        sem.release();
    }


    private boolean validateSet(int[] cards) { // check if set still exists on the table
        for (int i = 0; i < 3; i++) {
            if (table.cardToSlot[cards[i]] == null)
                return false;
        }
        return true;
    }


    public int setKind(int[] currSet) // 0 is nothing, 1 is score, 2 is penalty
    {
        if (env.util.testSet(currSet) && validateSet(currSet))
            return 1;
        if (env.util.testSet(currSet) && !validateSet(currSet))
            return 0;
        return 2;
    }

    private void startPlayers() {
        Thread[] threads = new Thread[players.length];
        for (int i = 0; i < players.length; i++) {
            threads[i] = new Thread(players[i]);
        }
        for (int i = 0; i < players.length; i++) {
            threads[i].start();
        }
        for (Player p : players) {
            p.state.set(true);
        }
    }
}












