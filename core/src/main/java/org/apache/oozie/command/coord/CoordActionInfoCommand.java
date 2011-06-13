/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. See accompanying LICENSE file.
 */
package org.apache.oozie.command.coord;

import java.util.List;

import org.apache.oozie.CoordinatorActionBean;
import org.apache.oozie.command.CommandException;
import org.apache.oozie.store.CoordinatorStore;
import org.apache.oozie.store.StoreException;
import org.apache.oozie.util.ParamChecker;
import org.apache.oozie.util.XLog;

public class CoordActionInfoCommand extends CoordinatorCommand<CoordinatorActionBean> {
    private String id;

    public CoordActionInfoCommand(String id) {
        super("action.info", "action.info", 1, XLog.OPS);
        this.id = ParamChecker.notEmpty(id, "id");
    }

    @Override
    protected CoordinatorActionBean call(CoordinatorStore store) throws StoreException, CommandException {
        CoordinatorActionBean action = store.getCoordinatorAction(id, false);
        return action;
    }

}
