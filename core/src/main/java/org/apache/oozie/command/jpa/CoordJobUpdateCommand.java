package org.apache.oozie.command.jpa;

import javax.persistence.EntityManager;
import org.apache.oozie.CoordinatorJobBean;
import org.apache.oozie.ErrorCode;
import org.apache.oozie.command.CommandException;
import org.apache.oozie.util.ParamChecker;

/**
 * Update the CoordinatorJob into a Bean and persist it.
 */
public class CoordJobUpdateCommand implements JPACommand<Void> {

    private CoordinatorJobBean coordJob = null;

    /**
     * @param coordJob
     */
    public CoordJobUpdateCommand(CoordinatorJobBean coordJob) {
        ParamChecker.notNull(coordJob, "CoordinatorJobBean");
        this.coordJob = coordJob;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.oozie.command.jpa.JPACommand#execute(javax.persistence.
     * EntityManager)
     */
    @Override
    public Void execute(EntityManager em) throws CommandException {
        try {
            em.merge(coordJob);
            return null;
        }
        catch (Exception e) {
            throw new CommandException(ErrorCode.E0603, e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.oozie.command.jpa.JPACommand#getName()
     */
    @Override
    public String getName() {
        return "CoordinatorUpdateJobCommand";
    }

}
