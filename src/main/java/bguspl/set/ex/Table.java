package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;
    protected Semaphore semPlace;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)
    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    protected int[][] playerTokens;
    protected int[] freeSpots;
    protected AtomicBoolean tableLock = new AtomicBoolean(false);

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        freeSpots = new int[slotToCard.length];
        for (int i = 0; i < slotToCard.length; i++)
            freeSpots[i] = 0;                 /// 0 = free place and 1 = occupied
        playerTokens = new int[env.config.players][4];
        for (int i = 0; i < env.config.players; i++) {
            for (int j = 0; j < 3; j++) {
                playerTokens[i][j] = -1;
            }
            playerTokens[i][3] = i;
        }
        semPlace = new Semaphore(1, true);
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }


    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        if (cardToSlot[card] == null) {
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot);
        }

    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        int card = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        env.ui.removeCard(slot);
        freeSpots[slot] = 0;
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) throws InterruptedException {
        semPlace.acquire();
        boolean flag = false;
        for (int i = 0; i < 3 && !flag; i++) {
            if (playerTokens[player][i] == -1) {
                playerTokens[player][i] = slot;
                env.ui.placeToken(player, slot);
                flag = true;
            }
        }

        semPlace.release();
    }


    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        for (int i = 0; i < 3; i++) {
            if (playerTokens[player][i] == slot) {
                env.ui.removeToken(player, slot);
                playerTokens[player][i] = -1;
                return true;
            }
        }
        return false;
    }

    public final int[] getTokens(int playerId) {
        return playerTokens[playerId];
    }


    public void removeAllTokens(int len) {
        env.ui.removeTokens();
        for (int i = 0; i < len; i++) {
            for (int j = 0; j < 3; j++) {
                playerTokens[i][j] = -1;
            }
        }
    }

    public void removeSet(int[] set) {
        int cnt = 0;
        for (int i = 0; i < set.length; i++) {
            for (int j = 0; j < playerTokens.length; j++) {  //remove tokens and cards set from table
                if (removeToken(j, cardToSlot[set[i]]))
                    cnt++;
            }
        }
        for (int i = 0; i < set.length; i++) { //// seperate the loops in order to ensure that card removed after token.
            removeCard(cardToSlot[set[i]]);
        }
    }
}





