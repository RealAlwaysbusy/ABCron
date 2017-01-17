package com.ab.abcron;

import java.io.IOException;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import anywheresoftware.b4a.BA;
import anywheresoftware.b4a.BA.Author;
import anywheresoftware.b4a.BA.DesignerName;
import anywheresoftware.b4a.BA.Events;
import anywheresoftware.b4a.BA.ShortName;
import anywheresoftware.b4a.BA.Version;

@DesignerName("Build 20161006")                                    
@Version(1.01F)                                
@Author("Alain Bailleul")
@Events(values={"Tick()","Finished()"})
@ShortName("ABCron") 
public class ABCron {
	protected static final int STATUS_UNDEFINED=-1;
	protected static final int STATUS_STOPPED=0;
	protected static final int STATUS_IDLE=1;
	protected static final int STATUS_RUNNING=2;
	protected static final int STATUS_FINISHED=3;
	protected boolean mIsInitialized=false;
	protected boolean mEnabled=false;	
	protected BA _ba = null;
	protected String _eventName = "";
	protected WCronExpression cronEx=null;
	private Date sTime = null;
    private Date eTime = null;
    private transient TimeZone timeZone = null;
	private long stTime=0;
	private long enTime=0;
	private String cronExp="";
	private String tzID="";
	protected ScheduledExecutorService service = null;
	protected Future<?> future = null;	
	protected Object caller=null;

	protected int mStatus=STATUS_UNDEFINED;
	
	/**	
     * if startTime = 0, then ASAP will be used
     * if endTime = 0, then it will repeat forever, following the cronExpression
     * if timeZoneID = "" then the default time zone will be used 
     * more info on how to build a Cron Expression: https://docs.oracle.com/cd/E12058_01/doc/doc.1014/e12030/cron_expressions.htm
     */
	public void Initialize(BA ba, Object callObject, String eventName, long startTime, long endTime, String timeZoneID, String cronExpression) {
		_ba = ba;
		_eventName = eventName.toLowerCase();
		this.stTime = startTime;
		this.enTime = endTime;
		this.tzID = timeZoneID;
		cronExp=cronExpression;
		caller = callObject;
		mIsInitialized=true;
		
		
	}
	
	Runnable runnable = new Runnable() {
	    public void run() {	    	
	    	if (mStatus==STATUS_RUNNING) {
	    		long delay = GetNextFireTimeAfter(0);
		        if (delay>0) {	  			        	
		        	_ba.raiseEvent(caller, _eventName + "_tick", new Object[] {});
		        	future = service.schedule(runnable, delay, TimeUnit.MILLISECONDS);
		        } else {
		        	mStatus = STATUS_STOPPED;
		        	_ba.raiseEvent(caller, _eventName + "_finished", new Object[] {});
		        	if (future!=null) {
		        		future.cancel(true);
		        	}
		        	if (service!=null) {
		        		service.shutdown();
		        	}
		        }
	    	}	    	
	    }
	};
	
	public void RestartApplicationNONUI() throws IOException {
	    try {
	        // java binary
	        String java = System.getProperty("java.home") + "/bin/java";
	       
	        // init the command to execute, add the vm args
	        final StringBuffer cmd = new StringBuffer("\"" + java + "\" ");	
	      
	        String jarName = new java.io.File(ABCron.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
	        BA.Log("About to restart: " + jarName);
	        
	        cmd.append("-jar " + jarName);   
	        
	     
	        Runtime.getRuntime().addShutdownHook(new Thread() {
	            @Override
	            public void run() {
	                try {
	                	BA.Log("Restarting: " + cmd.toString());
	                    Runtime.getRuntime().exec(cmd.toString());
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	        });
	        // execute some custom code before restarting
	        //if (runnableRestart != null) {
	        //	runnableRestart.run();
	        //}
	        // exit
	        mStatus = STATUS_STOPPED;		        	
		    if (future!=null) {
		        future.cancel(true);
		    }
		    if (service!=null) {
		    	service.shutdown();
		    }
		    _ba.stopMessageLoop();
	        //Runtime.getRuntime().exit(0);
	    } catch (Exception e) {
	        // something went wrong
	        throw new IOException("Error while trying to restart the application", e);
	    }
	}
		
	public boolean getIsInitialized() {
		return mIsInitialized;
	}
	
	public void setEnabled(boolean enabled) throws ParseException {
		if (enabled) {
			cronEx = new WCronExpression();
	    	//setCronExpression(cronExp);
	    	cronEx.Initialize(cronExp);

	        if (stTime == 0) {
	            sTime = new Date();
	        } else {
	        	sTime = new Date(stTime);
	        }
	        setStartTimeInternal(sTime);
	        if (enTime != 0) {
	        	setEndTimeInternal(new Date(enTime));
	        }
	        if (tzID.equals("")) {
	            setTimeZoneInternal(TimeZone.getDefault());
	        } else {
	            setTimeZoneInternal(TimeZone.getTimeZone(tzID));
	        }
	        long delay = GetNextFireTimeAfter(0);
	        if (delay>0) {	        	
	        	this.mStatus = STATUS_RUNNING;
	        	service = Executors.newSingleThreadScheduledExecutor();
	        	future = service.schedule(runnable, delay, TimeUnit.MILLISECONDS);
	        } else {
	        	mStatus = STATUS_STOPPED;
	        	_ba.raiseEvent(caller, _eventName + "_finished", new Object[] {});
	        	if (future!=null) {
	        		future.cancel(true);
	        	}
	        	if (service!=null) {
	        		service.shutdown();
	        	}
	        }
		} else {
			if (mStatus>STATUS_STOPPED) {
				mStatus = STATUS_STOPPED;
				future.cancel(true);
				service.shutdown();
			} else {
				this.mStatus = STATUS_STOPPED;
			}
			if (cronEx!=null) {
				cronEx = null;
			}
		}
		mEnabled=enabled;
	}	
	
	public boolean getEnabled() {
		return mEnabled;
	}
	
	public void setCronExpression(String cronExpression) {
		 cronExp = cronExpression;		
	}
	
	public String getCronExpression() {
		return cronExp;
	}
	
	public long getStartTime() {
        return this.stTime;
    }
    
    public void setStartTime(long startTime) {
    	stTime=startTime;
    }
    
    public long getEndTime() {
        return this.enTime;
    }
    
    public void setEndTime(long endTime) {
    	enTime=endTime;
    }
    
    public String getTimeZoneID() {
    	return tzID;
    }
    
    public void setTimeZoneID(String timeZoneID) {
    	tzID = timeZoneID;
    }
	
	protected Date getStartTimeInternal() {
        return this.sTime;
    }
    
    protected void setStartTimeInternal(Date startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }

        Date eTime = getEndTimeInternal();
        if (eTime != null && eTime.before(startTime)) {
            throw new IllegalArgumentException(
                "End time cannot be before start time");
        }
        
        // round off millisecond...
        // Note timeZone is not needed here as parameter for
        // Calendar.getInstance(),
        // since time zone is implicit when using a Date in the setTime method.
        Calendar cl = Calendar.getInstance();
        cl.setTime(startTime);
        cl.set(Calendar.MILLISECOND, 0);

        this.sTime = cl.getTime();
    }
    
    protected Date getEndTimeInternal() {
        return this.eTime;
    }
    
    protected void setEndTimeInternal(Date endTime) {
        Date sTime = getStartTimeInternal();
        if (sTime != null && endTime != null && sTime.after(endTime)) {
            throw new IllegalArgumentException(
                    "End time cannot be before start time");
        }

        this.eTime = endTime;
    }
    
    protected TimeZone getTimeZoneInternal() {        
        if(cronEx != null) {
            return cronEx.getTimeZone();
        }
        
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        return timeZone;
    }
    
    protected void setTimeZoneInternal(TimeZone timeZone) {
        if(cronEx != null) {
            cronEx.setTimeZone(timeZone);
        }
        this.timeZone = timeZone;
    }
    
    /**
     * Gets the next time it should be fired relative after the given time
     * returns 0 if no future time has been found 
     */
    public long GetNextFireTimeAfter(long afterTime) {
    	Date aTime = null;
        if (afterTime == 0) {
            aTime = new Date();
        } else {
        	aTime = new Date(afterTime);
        }

        if (getStartTimeInternal().after(aTime)) {
            aTime = new Date(getStartTimeInternal().getTime() - 1000l);
        }

        if (getEndTimeInternal() != null && (aTime.compareTo(getEndTimeInternal()) >= 0)) {
            return 0;
        }
        
        Date pot = getTimeAfter(aTime);
        if (getEndTimeInternal() != null && pot != null && pot.after(getEndTimeInternal())) {
            return 0;
        }
        
        ZoneId toZoneId = getTimeZoneInternal().toZoneId();
        ZonedDateTime from = ZonedDateTime.ofInstant(aTime.toInstant(),toZoneId);
        ZonedDateTime to = ZonedDateTime.ofInstant(pot.toInstant(), toZoneId);

        long milliseconds = java.time.Duration.between(from, to).toMillis();
        return milliseconds;
    }
    
    protected Date getFireTimeAfter(Date afterTime) {
        if (afterTime == null) {
            afterTime = new Date();
        }

        if (getStartTimeInternal().after(afterTime)) {
            afterTime = new Date(getStartTimeInternal().getTime() - 1000l);
        }

        if (getEndTimeInternal() != null && (afterTime.compareTo(getEndTimeInternal()) >= 0)) {
            return null;
        }
        
        Date pot = getTimeAfter(afterTime);
        if (getEndTimeInternal() != null && pot != null && pot.after(getEndTimeInternal())) {
            return null;
        }

        return pot;
    }
    
    protected Date getTimeAfter(Date afterTime) {
        return (cronEx == null) ? null : cronEx.getTimeAfter(afterTime);
    }
    
    
}
