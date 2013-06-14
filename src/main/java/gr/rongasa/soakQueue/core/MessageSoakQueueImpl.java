package gr.rongasa.soakQueue.core;/*

import gr.rongasa.soakQueue.api.Message;
import gr.rongasa.soakQueue.api.MessageSoakQueue;
import org.apache.log4j.Logger;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newScheduledThreadPool;


public class MessageSoakQueueImpl extends HashMap<String, MessageSoakQueueImpl.ItemContainer> implements MessageSoakQueue {
  private static final Logger log = Logger.getLogger(MessageSoakQueueImpl.class.getName());

  private final Object mapOperationLock = new SerializableLock();
  private final Object clockLock = new SerializableLock();
  private final int capacity;
  private final int validateAfter;
  private TreeMap<String, ItemContainer> sorted_map;
  private final ScheduledExecutorService scheduler = newScheduledThreadPool(1);
  private final Clock clock;
  private boolean timerIsRunning;
  private ScheduledFuture<?> schedule;

  @SuppressWarnings("unchecked")
  public MessageSoakQueueImpl(int capacity, int validateAfter) {
    super();
    this.capacity = capacity;
    this.validateAfter = validateAfter;
    ItemContainerComparator comparator = new ItemContainerComparator(this);
    sorted_map = new TreeMap(comparator);
    this.clock = new Clock(mapOperationLock);
  }

  @Override
  public void put(Message message) throws InterruptedException {
    synchronized (mapOperationLock) {
      info("Adding message: " + message.toString());
      if ((this.size() >= capacity) && !this.containsKey(message.getKey())) {
        log.error("Message overflow. Dumping message:" + message);
      } else {
        String key = message.getKey();
        Date increasedDate=getIncreasedDate();
        int refreshCounter=0;
        if (super.containsKey(key)){
          info("Message with key:"+key+" already queued. Updating message "+super.get(key).getMessage().toString());
          if (super.get(key).getRefreshCounter()>=2){
            increasedDate=super.get(key).getValidAfter();
          }
          refreshCounter=super.get(key).getRefreshCounter();
        }
        super.put(key, new ItemContainer(message, increasedDate,refreshCounter+1));
        shortItems();
        startClock();
        debug("Message added");
      }
    }
  }


  private void startClock() {
    synchronized (clockLock) {
      if (!timerIsRunning) {
        debug("Starting clock");
        schedule=scheduler.scheduleWithFixedDelay(clock, validateAfter/2, validateAfter/2, TimeUnit.SECONDS);
        timerIsRunning=true;
        debug("Clock started");
      }
    }
  }

  private void stopClock() {
    synchronized (clockLock) {
      if (timerIsRunning && this.size() == 0) {
        debug("Stopping clock");
        schedule.cancel(false);
        timerIsRunning=false;
        debug("Clock stopped");
      }
    }
  }


  @Override
  public Message take() throws InterruptedException {
    debug("take() triggered");
    Message message;
    synchronized (mapOperationLock) {
      try {
        while (this.size() >= 0 && !this.hasItems()){
          debug("take() going on wait mode.");
          mapOperationLock.wait();
          debug("take() awaken");
        }
      } catch (InterruptedException ie) {
        mapOperationLock.notify();
        throw ie;
      }
      debug("Retrieving item");
      Map.Entry<String, ItemContainer> entry = sorted_map.entrySet().iterator().next();
      message = entry.getValue().getMessage();
      String key = entry.getKey();
      sorted_map.remove(key);
      this.remove(key);
      stopClock();
      mapOperationLock.notify();
    }
    info("Retrieving operational state change message: " + message.toString());
    return message;
  }

  private boolean hasItems() {
    boolean result;
    if (sorted_map.size() != 0) {
      Map.Entry<String, ItemContainer> entry = sorted_map.entrySet().iterator().next();
      debug("Message: "+entry.getValue().getMessage()+" valid: "+entry.getValue().getValidAfter());
      result=entry.getValue().getValidAfter().compareTo(new Date()) <= 0;
    } else {
      result=false;
    }
    return result;
  }

  private void shortItems() {
    sorted_map.clear();
    sorted_map.putAll(this);
  }

  private Date getIncreasedDate() {
    Calendar now = Calendar.getInstance();
    now.add(Calendar.SECOND, validateAfter);
    return now.getTime();
  }

  private void info(String text){
    if (log.isInfoEnabled()){
       log.info(text);
    }
  }

  private void debug(String text){
    if (log.isDebugEnabled()){
       log.debug(text);
    }
  }

  private static class SerializableLock implements java.io.Serializable {
    private final static long serialVersionUID = -8856990691138858668L;
  }


  protected class ItemContainer {
    private Message message;
    private Date validAfter;
    private int refreshCounter=0;

    public ItemContainer(Message message, Date validAfter,int refreshCounter) {
      this.message = message;
      this.validAfter = validAfter;
      this.refreshCounter=refreshCounter;
    }

    public Message getMessage() {
      return message;
    }

    public Date getValidAfter() {
      return validAfter;
    }

    public int getRefreshCounter() {
      return refreshCounter;
    }

  }

  private class ItemContainerComparator implements Comparator<String> {
    private Map<String, ItemContainer> base;

    public ItemContainerComparator(Map<String, ItemContainer> base) {
      this.base = base;
    }

    public int compare(String item1, String item2) {
      int comparisonResult = base.get(item1).getValidAfter().compareTo(base.get(item2).getValidAfter());
      if (comparisonResult==0){
        comparisonResult=item1.compareTo(item2);
      }
      return comparisonResult;
    }
  }

  private class Clock implements Runnable {
    private final Object takeLock;

    private Clock(Object takeLock) {
      this.takeLock = takeLock;
    }

    @Override
    public void run() {
      debug("Clock invoked");
      synchronized (takeLock){
        debug("Clock invoked2");
         takeLock.notifyAll();
      }
      debug("Clock invoked successfully");
    }
  }
}