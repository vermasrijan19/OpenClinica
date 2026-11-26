package org.akaza.openclinica.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;

/**
 * Name-based lookups that Quartz 1.x offered directly on {@link Scheduler}
 * ({@code getTriggerNames(group)}, {@code getTriggerGroupNames()}) and that
 * Quartz 2.x replaced with key sets and matchers.
 */
public final class QuartzNames {

    private QuartzNames() {
    }

    /** Names of all triggers in {@code group}, sorted. */
    public static String[] triggerNames(Scheduler scheduler, String group) throws SchedulerException {
        List<String> names = new ArrayList<String>();
        for (TriggerKey key : scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(group))) {
            names.add(key.getName());
        }
        Collections.sort(names);
        return names.toArray(new String[names.size()]);
    }

    /** Names of all trigger groups. */
    public static String[] triggerGroupNames(Scheduler scheduler) throws SchedulerException {
        List<String> groups = scheduler.getTriggerGroupNames();
        return groups.toArray(new String[groups.size()]);
    }
}
