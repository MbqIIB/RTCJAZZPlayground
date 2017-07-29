package com.ibm.team.filesystem.cli.client.internal.querycommand.parser;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.TeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.dto.IBaselineSetSearchCriteria;
import com.ibm.team.scm.common.dto.IBaselineSetSearchCriteria.WorkspaceSearchCriteriaFactory;
import com.ibm.team.scm.common.dto.IComponentSearchCriteria;
import com.ibm.team.scm.common.dto.IComponentSearchCriteria.ComponentSearchCriteriaFactory;
import com.ibm.team.scm.common.dto.QueryOperations;
import com.ibm.team.scm.common.internal.rest.IScmRestService2;
import com.ibm.team.scm.common.internal.rest.IScmRestService2.ParmsSearchWorkspaces;
import com.ibm.team.scm.common.internal.rest.dto.WorkspaceDTO;
import com.ibm.team.scm.common.internal.rest2.dto.WorkspaceSearchResultDTO;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;










public final class QueryParserUtil
{
  public QueryParserUtil() {}
  
  private static IProjectArea getProjectAreaByName(String projectAreaName, ITeamRepository repo)
  {
    IProcessItemService itemService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
    
    try
    {
      projectAreas = itemService.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, null);
    } catch (TeamRepositoryException localTeamRepositoryException) { List<IProjectArea> projectAreas;
      throw new RuntimeException(Messages.QueryBuilder_LookupFailure); }
    List<IProjectArea> projectAreas;
    for (IProjectArea pa : projectAreas) {
      if (pa.getName().equals(projectAreaName)) {
        return pa;
      }
    }
    throw new RuntimeException(NLS.bind(Messages.QueryBuilder_NoSuchProjectArea, projectAreaName));
  }
  
  private static ITeamArea getTeamAreaByName(String teamAreaName, ITeamRepository repo)
  {
    IProcessItemService itemService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
    try
    {
      List<IProjectArea> projectAreas = itemService.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, null);
      Iterator localIterator2; for (Iterator localIterator1 = projectAreas.iterator(); localIterator1.hasNext(); 
          

          localIterator2.hasNext())
      {
        IProjectArea projectArea = (IProjectArea)localIterator1.next();
        List<ITeamArea> teamAreas = repo.itemManager().fetchPartialItems(projectArea.getTeamAreas(), 0, 
          Collections.singletonList("name"), null);
        localIterator2 = teamAreas.iterator(); continue;ITeamArea teamArea = (ITeamArea)localIterator2.next();
        if (teamArea.getName().equals(teamAreaName)) {
          return teamArea;
        }
      }
    }
    catch (TeamRepositoryException localTeamRepositoryException) {
      throw new RuntimeException(Messages.QueryBuilder_LookupFailure);
    }
    throw new RuntimeException(NLS.bind(Messages.QueryBuilder_NoSuchTeamArea, teamAreaName));
  }
  
  private static IComponentHandle getComponentByName(String componentName, ITeamRepository repo) {
    IComponentSearchCriteria criteria = IComponentSearchCriteria.FACTORY.newInstance();
    criteria.setExactName(componentName);
    
    try
    {
      compHandles = SCMPlatform.getWorkspaceManager(repo).findComponents(criteria, Integer.MAX_VALUE, null);
    } catch (TeamRepositoryException localTeamRepositoryException) { List<IComponentHandle> compHandles;
      throw new RuntimeException(Messages.QueryBuilder_LookupFailure); }
    List<IComponentHandle> compHandles;
    if (compHandles.size() == 1) {
      return (IComponentHandle)compHandles.get(0);
    }
    throw new RuntimeException(NLS.bind(Messages.QueryBuilder_NoSuchComponent, componentName));
  }
  
  private static IWorkspace getWorkspaceByName(String workspaceName, boolean getStream, ITeamRepository repo)
  {
    IScmRestService2 scmRestService = (IScmRestService2)((TeamRepository)repo).getServiceInterface(IScmRestService2.class);
    
    IScmRestService2.ParmsSearchWorkspaces workspaceSearchParms = new IScmRestService2.ParmsSearchWorkspaces();
    workspaceName = workspaceName;
    workspaceNameKind = "exact";
    if (getStream) {
      workspaceKind = "streams";
    } else {
      workspaceKind = "workspaces";
    }
    try {
      WorkspaceSearchResultDTO searchWorkspaces = scmRestService.getSearchWorkspaces(workspaceSearchParms);
      for (Object workspaceDTO : searchWorkspaces.getWorkspaces()) {
        IWorkspace workspace = ((WorkspaceDTO)workspaceDTO).getWorkspace();
        if ((workspace.getName().equals(workspaceName)) && (workspace.isStream() == getStream)) {
          return workspace;
        }
      }
    } catch (TeamRepositoryException localTeamRepositoryException) {
      throw new RuntimeException(Messages.QueryBuilder_LookupFailure);
    }
    throw new RuntimeException(NLS.bind(Messages.QueryBuilder_NoSuchWorkspace, workspaceName));
  }
  
  private static IBaselineSetHandle getSnapshotByName(String snapshotName, ITeamRepository repo)
  {
    IBaselineSetSearchCriteria criteria = IBaselineSetSearchCriteria.FACTORY.newInstance();
    criteria.setExactName(snapshotName);
    try
    {
      baselineSetHandles = SCMPlatform.getWorkspaceManager(repo).findBaselineSets(criteria, Integer.MAX_VALUE, null);
    } catch (TeamRepositoryException localTeamRepositoryException) { List<IBaselineSetHandle> baselineSetHandles;
      throw new RuntimeException(Messages.QueryBuilder_LookupFailure); }
    List<IBaselineSetHandle> baselineSetHandles;
    if (baselineSetHandles.size() > 0) {
      return (IBaselineSetHandle)baselineSetHandles.get(0);
    }
    throw new RuntimeException(NLS.bind(Messages.QueryBuilder_NoSuchSnapshot, snapshotName));
  }
  









  public static UUID getUUIDForName(String functionName, String objectName, ITeamRepository repo)
  {
    IItemHandle itemHandle = null;
    if (functionName.equals(QueryOperations.OPERATION_IN_PROJECT_AREA)) {
      itemHandle = getProjectAreaByName(objectName, repo);
    } else if (functionName.equals(QueryOperations.OPERATION_IN_TEAM_AREA)) {
      itemHandle = getTeamAreaByName(objectName, repo);
    } else if (functionName.equals(QueryOperations.OPERATION_FOR_COMPONENT)) {
      itemHandle = getComponentByName(objectName, repo);
    } else if ((functionName.equals(QueryOperations.OPERATION_IN_STREAM)) || (functionName.equals(QueryOperations.OPERATION_FOR_STREAM))) {
      itemHandle = getWorkspaceByName(objectName, true, repo);
    } else if ((functionName.equals(QueryOperations.OPERATION_IN_WORKSPACE)) || (functionName.equals(QueryOperations.OPERATION_FOR_WORKSPACE))) {
      itemHandle = getWorkspaceByName(objectName, false, repo);
    } else if (functionName.equals(QueryOperations.OPERATION_IN_SNAPSHOT)) {
      itemHandle = getSnapshotByName(objectName, repo);
    }
    if (itemHandle != null) {
      return itemHandle.getItemId();
    }
    return null;
  }
  
  private static String removePrefix(String rawString, char prefix) {
    StringBuilder bufString = new StringBuilder(rawString);
    if ((bufString.length() > 0) && 
      (bufString.charAt(0) == prefix))
    {
      bufString.deleteCharAt(0);
    }
    
    return bufString.toString();
  }
  





  public static UUID getUUIDForAlias(String aliasString)
  {
    String aliasValue = removePrefix(aliasString, '@');
    

    IUuidAliasRegistry.IUuidAlias alias = RepoUtil.lookupUuidAndAlias(aliasValue);
    if (alias == null)
    {
      throw new RuntimeException(NLS.bind(Messages.QueryBuilder_UnknownAlias, aliasValue));
    }
    
    return alias.getUuid();
  }
}
