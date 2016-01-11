/**
 * This file is part of mycollab-mobile.
 *
 * mycollab-mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-mobile.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.mobile.module.project.view.task;

import com.esofthead.mycollab.common.GenericLinkUtils;
import com.esofthead.mycollab.mobile.ui.AbstractListPresenter;
import com.esofthead.mycollab.module.project.CurrentProjectVariables;
import com.esofthead.mycollab.module.project.ProjectRolePermissionCollections;
import com.esofthead.mycollab.module.project.domain.SimpleTask;
import com.esofthead.mycollab.module.project.domain.criteria.TaskSearchCriteria;
import com.esofthead.mycollab.module.project.i18n.TaskI18nEnum;
import com.esofthead.mycollab.vaadin.AppContext;
import com.esofthead.mycollab.vaadin.mvp.ScreenData;
import com.esofthead.mycollab.vaadin.ui.NotificationUtil;
import com.vaadin.ui.ComponentContainer;

/**
 * @author MyCollab Ltd.
 * @since 4.5.0
 */
public class TaskListPresenter extends AbstractListPresenter<TaskListView, TaskSearchCriteria, SimpleTask> {
    private static final long serialVersionUID = -2899902106379842031L;

    public TaskListPresenter() {
        super(TaskListView.class);
    }

    @Override
    protected void onGo(ComponentContainer container, ScreenData<?> data) {
        if (CurrentProjectVariables.canRead(ProjectRolePermissionCollections.BUGS)) {
            TaskSearchCriteria param = (TaskSearchCriteria) data.getParams();
            super.onGo(container, data);
            this.doSearch(param);
            AppContext.addFragment("project/task/list/" + GenericLinkUtils.encodeParam(CurrentProjectVariables.getProjectId()),
                    AppContext.getMessage(TaskI18nEnum.M_VIEW_LIST_TITLE));
        } else {
            NotificationUtil.showMessagePermissionAlert();
        }
    }
}
