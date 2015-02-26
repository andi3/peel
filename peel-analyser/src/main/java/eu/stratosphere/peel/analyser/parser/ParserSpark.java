package eu.stratosphere.peel.analyser.parser;

import eu.stratosphere.peel.analyser.exception.PeelAnalyserException;
import eu.stratosphere.peel.analyser.model.ExperimentRun;
import eu.stratosphere.peel.analyser.model.Task;
import eu.stratosphere.peel.analyser.model.TaskInstance;
import eu.stratosphere.peel.analyser.model.TaskInstanceEvents;
import eu.stratosphere.peel.analyser.parser.events.Event;
import eu.stratosphere.peel.analyser.util.HibernateUtil;
import eu.stratosphere.peel.analyser.util.ORM;
import org.json.JSONObject;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Fabian on 02.11.2014.
 */
public class ParserSpark implements Parser {
  private ExperimentRun experimentRun;
  private Date firstEntry = null;
  private Date lastEntry = null;
  private boolean skipInstances;
  private ORM orm = HibernateUtil.getORM();
  Set<Event> events = new HashSet<>();
  private static final Logger LOGGER = LoggerFactory
		  .getLogger(ParserSpark.class);

  public ParserSpark(boolean skipInstances) {
    this.skipInstances = skipInstances;
    findEvents();
  }

  public ParserSpark(ExperimentRun experimentRun) {
    this.experimentRun = experimentRun;
  }

  public ExperimentRun getExperimentRun() {
    return experimentRun;
  }

  public void setExperimentRun(ExperimentRun experimentRun) {
    this.experimentRun = experimentRun;
  }

  public void parse(BufferedReader in)
		  throws IOException, PeelAnalyserException {
    Task task;
    orm.beginTransaction();

    String line;
    while ((line = in.readLine()) != null) {
      if (ParserSparkHelper.getEvent(line).equals("SparkListenerTaskEnd")
		      && !skipInstances) {
	task = getTaskByLine(line);
	task.getTaskInstances().add(getTaskInstanceByLine(line, task));
	orm.update(experimentRun);
      } else if (ParserSparkHelper.getEvent(line)
		      .equals("SparkListenerApplicationStart")) {
	setSubmitTime(line);
      } else if (ParserSparkHelper.getEvent(line).equals("SparkListenerTaskEnd")
		      && skipInstances) {
	if (lastEntry == null) {
	  lastEntry = ParserSparkHelper.getFinishTime(line);
	} else if (lastEntry.getTime() < ParserSparkHelper.getFinishTime(line)
			.getTime()) {
	  lastEntry = ParserSparkHelper.getFinishTime(line);
	}
	if (firstEntry == null) {
	  firstEntry = ParserSparkHelper.getLaunchTime(line);
	}
      }
    }
    experimentRun.setDeployed(firstEntry);
    experimentRun.setFinished(lastEntry);
    orm.update(experimentRun);
    orm.commitTransaction();
  }

  /**
   * this method will parse the line and will create a new TaskInstance object with the parsed information
   *
   * @param input a logfile line
   * @return TaskInstance with all related events
   */
  private TaskInstance getTaskInstanceByLine(String input, Task task) {
    TaskInstance taskInstance = new TaskInstance();
    taskInstance.setTask(task);
    taskInstance.setSubTaskNumber(ParserSparkHelper.getTaskID(input));
    orm.save(taskInstance);

    TaskInstanceEvents eventLaunch = new TaskInstanceEvents();
    eventLaunch.setEventName("Launch");
    eventLaunch.setValueTimestamp(ParserSparkHelper.getLaunchTime(input));
    taskInstance.getTaskInstanceEventsSet().add(eventLaunch);
    eventLaunch.setTaskInstance(taskInstance);
    orm.save(eventLaunch);

    if (firstEntry == null) {
      firstEntry = eventLaunch.getValueTimestamp();
    }

    TaskInstanceEvents eventFinished = new TaskInstanceEvents();
    eventFinished.setEventName("Finished");
    eventFinished.setValueTimestamp(ParserSparkHelper.getFinishTime(input));
    taskInstance.getTaskInstanceEventsSet().add(eventFinished);
    eventFinished.setTaskInstance(taskInstance);
    orm.save(eventFinished);

    if (lastEntry == null) {
      lastEntry = eventFinished.getValueTimestamp();
    } else if (lastEntry.getTime() < eventFinished.getValueTimestamp()
		    .getTime()) {
      lastEntry = eventFinished.getValueTimestamp();
    }

    Set<TaskInstanceEvents> otherEvents = getTaskInstanceEventsByLine(input);
    for (TaskInstanceEvents event : otherEvents) {
      event.setTaskInstance(taskInstance);
      taskInstance.getTaskInstanceEventsSet().add(event);
      orm.save(event);
    }

    return taskInstance;
  }

  /**
   * returns the task of the given line. If the task is not created yet, it will create it.
   *
   * @param input - a String line
   * @return Task of given line
   */
  private Task getTaskByLine(String input) {
    String taskType = ParserSparkHelper.getTaskType(input);
    Task task = experimentRun.taskByTaskType(taskType);
    if (task == null) {
      task = new Task();
      task.setExperimentRun(experimentRun);
      task.setTaskType(taskType);
      experimentRun.getTaskSet().add(task);
      orm.save(task);
    }
    return task;
  }

  private void setSubmitTime(String line) {
    JSONObject jsonObject = new JSONObject(line);
    experimentRun.setSubmitTime(new Date(jsonObject.getLong("Timestamp")));
  }

  private Set<TaskInstanceEvents> getTaskInstanceEventsByLine(String line) {
    JSONObject jsonObject = new JSONObject(line);
    HashSet<TaskInstanceEvents> taskInstanceEventsHashSet = new HashSet<>();
    for (Event event : events) {
      String result = getJsonEntry(jsonObject, event.eventName());
      if (result != null) {
	taskInstanceEventsHashSet.add(convertToEvent(line, event.eventName(),
			event.eventType()));
      }
    }
    return taskInstanceEventsHashSet;
  }

  private String getJsonEntry(JSONObject jsonObject, String name) {
    String result = jsonObject.getString(name);
    if (result != null) {
      return result;
    }
    Set<String> childObjects = jsonObject.keySet();
    for (String child : childObjects) {
      JSONObject childJSON = jsonObject.getJSONObject(child);
      if (childJSON != null) {
	result = getJsonEntry(childJSON, name);
	if (result != null) {
	  return result;
	}
      }
    }
    return null;
  }

  private TaskInstanceEvents convertToEvent(String input, String name,
		  Class<?> convertType) {
    TaskInstanceEvents event = new TaskInstanceEvents();
    event.setEventName(name);
    try {
      if (convertType == String.class) {
	event.setValueVarchar(input);
	return event;
      } else if (convertType == Double.class) {
	event.setValueDouble(Double.valueOf(input));
	return event;
      } else if (convertType == Integer.class) {
	event.setValueInt(Integer.valueOf(input));
	return event;
      } else if (convertType == Date.class) {
	event.setValueTimestamp(new Date(Long.valueOf(input)));
	return event;
      }
    } catch (Exception e) {
      LOGGER.error("Error in converting the input value " + input
		      + " into type " + convertType.getCanonicalName(), e);
    }
    LOGGER.error("Wasn't able to convert to type " + convertType);
    return null;
  }

  private void findEvents() {
    Reflections reflections = new Reflections("eu.stratosphere.peel.analyser");
    Set<Class<? extends Event>> eventClasses = reflections
		    .getSubTypesOf(Event.class);
    for (Class<? extends Event> eventClass : eventClasses) {
      try {
	events.add(eventClass.newInstance());
      } catch (InstantiationException e) {
	LOGGER.error("could not instantiate the event", e);
      } catch (IllegalAccessException e) {
	LOGGER.error("could not access the event", e);
      }
    }
  }
}