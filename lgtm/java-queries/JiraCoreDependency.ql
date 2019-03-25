/**
 * @name Direct dependency on jira core
 * @description Plugins should not directly depend on classes defined in jira-core. 
 *              Instead they should access them through components exposed by jira-api.
 * @kind problem
 * @problem.severity warning
 * @precision very-high
 * @id jira/jira-core-dependency
 * @tags reliability
         maintainability
 */

import java

from Expr access, File file
where file.getParentContainer+().getBaseName().matches("jira-core-%.jar")
and (file = access.(TypeAccess).getType().getFile() or
    file = access.(MethodAccess).getMethod().getFile())
select access, "Dependency on jira-core"
