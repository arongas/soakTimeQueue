package gr.rongasa.soakQueue.core;

import gr.rongasa.soakQueue.api.Message;
import gr.rongasa.soakQueue.api.MessageSoakQueue;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class MessageSoakQueueImplTest {
  private static SimpleDateFormat dt = new SimpleDateFormat("yyyyy-mm-dd hh:mm:ss");
  MessageSoakQueue messageSoakQueue;

  @Before
  public void onSetup() {
    messageSoakQueue = new MessageSoakQueueImpl(100, 20);
  }

  boolean finished=false;

  @Test
  public void functionalityTest() throws InterruptedException {
    Thread thr1=new Thread(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < 10; i++) {
          for (int j = 0; j < 10; j++) {
            try {
              messageSoakQueue.put(new MessageImpl(j + ""));
              Thread.sleep(200);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
        }
        finished=true;
      }
    });

    Thread thr2=new Thread(new Runnable() {
      @Override
      public void run() {
        while (((Map)messageSoakQueue).size()!=0 || !finished){
        try {
          MessageImpl message=(MessageImpl)messageSoakQueue.take();
          Date takeTime=new Date();

          long diff=(takeTime.getTime()-message.getCreationDate().getTime())/1000;
          if (diff<20 || diff>22){
            int x=0;
          }
          System.out.println("Message received:"+message+"difference from expected:"+diff);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }});
    thr1.start();
    thr2.start();
    thr1.join();

    thr2.join();
  }


  public class MessageImpl implements Message {
    Date creationDate;
    String id;

    public MessageImpl(String id) {
      this.id = id;
      creationDate = new Date();
    }

    public Date getCreationDate() {
      return creationDate;
    }

    @Override
    public String getKey() {
      return id;
    }

    @Override
    public String toString() {
      return "MessageImpl{" +
        "creationDate=" + dt.format(creationDate) +
        ", id='" + id + '\'' +
        '}';
    }
  }
}
