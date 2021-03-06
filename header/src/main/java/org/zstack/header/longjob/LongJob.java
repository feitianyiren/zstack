package org.zstack.header.longjob;

import org.zstack.header.core.Completion;

/**
 * Created by GuoYi on 11/24/17.
 */
public interface LongJob {
    void start(LongJobVO job, Completion completion);
    void cancel(LongJobVO job, Completion completion);
    default void resume(LongJobVO job) {
        job.setJobResult("Failed because management node restarted.");
        job.setState(LongJobState.Failed);
    }
}
