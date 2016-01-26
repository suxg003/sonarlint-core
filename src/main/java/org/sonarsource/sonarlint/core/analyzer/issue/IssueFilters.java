/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.analyzer.issue;

import org.sonar.api.batch.BatchSide;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilter;
import org.sonar.api.scan.issue.filter.IssueFilterChain;

@BatchSide
public class IssueFilters {
  private final IssueFilter[] filters;
  private final Project project;

  public IssueFilters(Project project, IssueFilter[] exclusionFilters) {
    this.project = project;
    this.filters = exclusionFilters;
  }

  public IssueFilters(Project project) {
    this(project, new IssueFilter[0]);
  }

  public boolean accept(String componentKey, org.sonar.api.batch.sensor.issue.Issue rawIssue) {
    IssueFilterChain filterChain = new DefaultIssueFilterChain(filters);
    FilterableIssue fIssue = new DefaultFilterableIssue(project, rawIssue, componentKey);
    return filterChain.accept(fIssue);
  }

}
