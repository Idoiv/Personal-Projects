package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealerTest {

    private Dealer dealer;
    private Env env;
    private Config config;
    private Table table;
    private Player []players;


   void assertInvariants() {

        }


    @BeforeEach
    void setUp() {

        createTableForDealer();

        //create env for player
        Properties properties = new Properties();
        properties.put("deckSize",10);
        TableTest.MockLogger logger = new TableTest.MockLogger();
        config = new Config(logger, properties);
        Env env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());

        Player p1 = new Player(env,dealer,table,0,true);
        Player p2 = new Player(env,dealer,table,1,true);

        players=new Player[2];
        players[0]=p1;
        players[1]=p2;

        dealer = new Dealer(env, table, players);

    }



    @Test
     void removeAllCardsFromTable() {

        for(int i = 0; i < table.slotToCard.length ; i ++){
            System.out.println(table.slotToCard[i]);
        }

        dealer.removeAllCardsFromTable();


        // verify all slots are empty
        for(int i = 0; i < table.slotToCard.length ; i ++){
            assertEquals(null, table.slotToCard[i]);
        }
        // verify all cards do not have slots.
        for(int i = 0; i < table.cardToSlot.length ; i ++){
            assertEquals(null, table.cardToSlot[i]);
        }


    }


    @Test
     void announceWinner()
    {
        assertEquals(0, players[0].score());
        players[0].point();
        assertEquals(1, players[0].score());
        int Win= dealer.announceWinners();
        assertEquals(0, Win);
    }


    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    void createTableForDealer()
    {
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        Integer[] slotToCard = new Integer[config.tableSize];
        Integer[] cardToSlot = new Integer[config.deckSize];
        Env env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());
        this.table = new Table(env, slotToCard, cardToSlot);
        fillSlotForTable();

    }

    void fillSlotForTable()
    {
        table.slotToCard[0]=0;
        table.slotToCard[1]=1;
        table.slotToCard[2]=2;
        table.slotToCard[3]=3;

        table.cardToSlot[0]=0;
        table.cardToSlot[1]=1;
        table.cardToSlot[2]=2;
        table.cardToSlot[3]=3;
    }

}
