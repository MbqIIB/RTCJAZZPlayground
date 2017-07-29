package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ConnectionInfo;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmHandle;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmWorkspace2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmWorkspaceList;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsSearchWorkspaces;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.osgi.util.NLS;



















public class ListCmd
  extends AbstractSubcommand
{
  private static final String LIST_PROJECT_AREAS_ID = "com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-projectareas";
  private static final String LIST_TEAM_AREAS_ID = "com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-teamareas";
  private static final String LIST_STREAMS_ID = "com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-streams";
  private static final String LIST_WORKSPACES_ID = "com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-workspaces";
  public ListCmd() {}
  
  private static class WorkspaceComparator
    implements Comparator<ScmWorkspace>
  {
    private WorkspaceComparator() {}
    
    public int compare(ScmWorkspace o1, ScmWorkspace o2)
    {
      String ws1 = o1.getName();
      String ws2 = o2.getName();
      
      if (ws1 == null) {
        return 1;
      }
      if (ws2 == null) {
        return -1;
      }
      return ws1.compareToIgnoreCase(ws2);
    }
  }
  
  static enum ListableThing {
    PROJECT_AREAS,  TEAM_AREAS,  STREAMS,  WORKSPACES,  CONTRIBUTORS;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    ConnectionInfo connection = config.getConnectionInfo();
    
    if ((subargs.hasOption(ListStreamsOptions.OPT_PROJECTAREA)) && (subargs.hasOption(ListStreamsOptions.OPT_TEAMAREA))) {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        subargs.getDefinition().getOption(ListStreamsOptions.OPT_PROJECTAREA).getName(), 
        subargs.getDefinition().getOption(ListStreamsOptions.OPT_TEAMAREA).getName()));
    }
    
    boolean verbose = subargs.hasOption(CommonOptions.OPT_VERBOSE);
    
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    String contribName = null;
    if (subargs.hasOption(ListCmdContribOptions.OPT_CONTRIB)) {
      contribName = subargs.getOption(ListCmdContribOptions.OPT_CONTRIB);
    }
    
    String id = config.getSubcommandDefinition().getId();
    
    if ("com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-projectareas".equals(id)) {
      listProjectAreas(config, client, connection, verbose);
    }
    else if ("com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-teamareas".equals(id)) {
      listTeamAreas(config, client, connection, contribName, verbose);
    }
    else if (("com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-streams".equals(id)) || ("com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-workspaces".equals(id))) {
      listWorkspaces(config, client, connection, contribName, "com.ibm.team.filesystem.cli.client.list/com.ibm.team.filesystem.cli.client.list-workspaces".equals(id), verbose, subargs);
    }
    else {
      throw StatusHelper.configurationFailure(NLS.bind(Messages.ListCmd_1, id));
    }
  }
  
  private void listProjectAreas(IScmClientConfiguration config, IFilesystemRestClient client, ConnectionInfo connection, boolean verbose)
    throws FileSystemException
  {
    JSONArray jProjAreas = jsonizeProjectAreas(config, client, connection, verbose);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jProjAreas);
      return;
    }
    
    if (jProjAreas.size() == 0) {
      return;
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    for (Object obj : jProjAreas)
    {
      JSONObject jProjArea = (JSONObject)obj;
      
      out.println(AliasUtil.selector(config.getAliasConfig(), (String)jProjArea.get("name"), 
        UUID.valueOf((String)jProjArea.get("uuid")), (String)jProjArea.get("url"), RepoUtil.ItemType.PROJECTAREA));
      
      JSONArray jTeamAreas = (JSONArray)jProjArea.get("team-areas");
      
      if (jTeamAreas != null)
      {


        for (Object jobj : jTeamAreas) {
          JSONObject jTeamArea = (JSONObject)jobj;
          
          out.indent().println(AliasUtil.selector(config.getAliasConfig(), (String)jTeamArea.get("name"), 
            UUID.valueOf((String)jTeamArea.get("uuid")), 
            (String)jTeamArea.get("url"), RepoUtil.ItemType.TEAMAREA));
        }
      }
    }
  }
  
  private JSONArray jsonizeProjectAreas(IScmClientConfiguration config, IFilesystemRestClient client, ConnectionInfo connection, boolean verbose)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.login(config, client, connection);
    

    List<IProjectArea> projectAreas = RepoUtil.findProjectAreas(repo, config);
    
    JSONArray jProjAreas = new JSONArray();
    
    for (IProjectArea projectArea : projectAreas)
    {
      JSONObject jProjArea = JSONPrintUtil.jsonize(projectArea.getName(), projectArea.getItemId().getUuidValue(), repo.getRepositoryURI());
      

      if (verbose)
      {
        try {
          teamAreas = repo.itemManager().fetchPartialItems(projectArea.getTeamAreas(), 0, Collections.singletonList("name"), null);
        } catch (TeamRepositoryException e) { List<ITeamArea> teamAreas;
          throw StatusHelper.wrap(NLS.bind(Messages.ListCmd_4, projectArea.getName()), e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
        }
        List<ITeamArea> teamAreas;
        JSONArray jTeamAreas = new JSONArray();
        for (ITeamArea teamArea : teamAreas) {
          JSONObject jTeamArea = JSONPrintUtil.jsonize(teamArea.getName(), teamArea.getItemId().getUuidValue(), repo.getRepositoryURI());
          jTeamAreas.add(jTeamArea);
        }
        
        if (jTeamAreas.size() > 0) {
          jProjArea.put("team-areas", jTeamAreas);
        }
      }
      jProjAreas.add(jProjArea);
    }
    
    return jProjAreas;
  }
  
  private void listTeamAreas(IScmClientConfiguration config, IFilesystemRestClient client, ConnectionInfo connection, String contribId, boolean verbose)
    throws FileSystemException
  {
    JSONArray jTeamAreas = jsonizeTeamAreas(config, client, connection, contribId, verbose);
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jTeamAreas);
      return;
    }
    
    if (jTeamAreas.size() == 0) {
      return;
    }
    
    for (Object obj : jTeamAreas) {
      JSONObject jTeamArea = (JSONObject)obj;
      
      config.getContext().stdout().println(
        AliasUtil.selector(config.getAliasConfig(), (String)jTeamArea.get("name"), 
        UUID.valueOf((String)jTeamArea.get("uuid")), 
        (String)jTeamArea.get("url"), RepoUtil.ItemType.TEAMAREA));
    }
  }
  
  private JSONArray jsonizeTeamAreas(IScmClientConfiguration config, IFilesystemRestClient client, ConnectionInfo connection, String contribId, boolean verbose)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.login(config, client, connection);
    List<ITeamArea> teamAreas;
    List<ITeamArea> teamAreas;
    if (contribId == null) {
      teamAreas = RepoUtil.findTeamAreas(config, repo);
    } else {
      teamAreas = RepoUtil.findTeamAreas(config, contribId, repo);
    }
    
    JSONArray jTeamAreas = new JSONArray();
    
    for (ITeamArea teamArea : teamAreas)
    {
      JSONObject jTeamArea = JSONPrintUtil.jsonize(teamArea.getName(), teamArea.getItemId().getUuidValue(), repo.getRepositoryURI());
      
      jTeamAreas.add(jTeamArea);
    }
    
    return jTeamAreas;
  }
  










  private void listWorkspaces(IScmClientConfiguration config, IFilesystemRestClient client, ConnectionInfo connection, String contribName, boolean fetchWorkspace, boolean verbose, ICommandLine subargs)
    throws FileSystemException
  {
    String projectAreaName = null;
    if (subargs.hasOption(ListStreamsOptions.OPT_PROJECTAREA)) {
      projectAreaName = subargs.getOption(ListStreamsOptions.OPT_PROJECTAREA);
    }
    String teamAreaName = null;
    if (subargs.hasOption(ListStreamsOptions.OPT_TEAMAREA)) {
      if (subargs.hasOption(ListCmdContribOptions.OPT_CONTRIB)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ListCmd_MUTUALLYEXCLUSIVE_CONTRIB_TEAMAREA, 
          subargs.getDefinition().getOption(ListCmdContribOptions.OPT_CONTRIB).getName(), 
          subargs.getDefinition().getOption(ListStreamsOptions.OPT_TEAMAREA).getName()));
      }
      teamAreaName = subargs.getOption(ListStreamsOptions.OPT_TEAMAREA);
    }
    

    ITeamRepository repo = RepoUtil.login(config, client, connection);
    
    IProjectArea projectArea = null;
    if ((projectAreaName != null) && (projectAreaName.length() != 0))
    {
      projectArea = RepoUtil.getProjectArea(repo, projectAreaName, config);
      
      if (projectArea == null) {
        throw StatusHelper.failure(NLS.bind(Messages.ListCmd_NOPROJECTAREA, projectAreaName), null);
      }
    }
    
    IContributor contributor = null;
    if ((contribName != null) && (contribName.length() > 0)) {
      contributor = RepoUtil.getContributor(contribName, repo, config);
    }
    
    ITeamArea teamArea = RepoUtil.getTeamArea(teamAreaName, projectArea, config, repo);
    if ((teamArea == null) && (teamAreaName != null)) {
      config.getContext().stderr().println(NLS.bind(Messages.ListCmd_TeamAreaNotFound, teamAreaName));
      return;
    }
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    boolean hasMoreItems = false;
    List<ScmWorkspace> workspaces = new ArrayList();
    hasMoreItems = getWorkspaces(projectArea, teamArea, contributor, scmService, fetchWorkspace, workspaces, subargs, repo, config);
    
    printWorkspaces(workspaces, repo, verbose, client, config, hasMoreItems);
  }
  

  private boolean getWorkspaces(IProjectArea projectArea, ITeamArea teamArea, IContributor contributor, IScmRichClientRestService scmService, boolean fetchWorkspace, List<ScmWorkspace> workspaces, ICommandLine subargs, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    int maxResults = RepoUtil.getMaxResultsOption(subargs);
    
    IScmRichClientRestService.ParmsSearchWorkspaces parms = new IScmRichClientRestService.ParmsSearchWorkspaces();
    workspaceKind = (fetchWorkspace ? "workspaces" : "streams");
    maxResultSize = maxResults;
    includeLockOwner = true;
    
    List<IAuditableHandle> owners = new ArrayList();
    if (projectArea != null) {
      owners.add(projectArea);
      owners.addAll(projectArea.getTeamAreas());
    }
    if (teamArea != null) {
      owners.add(teamArea);
    }
    if (contributor != null) {
      owners.add(contributor);
    }
    
    if (!owners.isEmpty()) {
      ownerItemId = new String[owners.size()];
      ownerType = new String[owners.size()];
      for (int i = 0; i < owners.size(); i++) {
        IAuditableHandle handle = (IAuditableHandle)owners.get(i);
        ownerItemId[i] = handle.getItemId().getUuidValue();
        if (IProjectArea.ITEM_TYPE.equals(handle.getItemType())) {
          ownerType[i] = "projectarea";
        } else if (ITeamArea.ITEM_TYPE.equals(handle.getItemType())) {
          ownerType[i] = "teamarea";
        } else {
          ownerType[i] = "contributor";
        }
      }
    }
    
    if (subargs.hasOption(ListWorkspacesOptions.OPT_NAME_FILTER)) {
      String nameSelector = subargs.getOption(ListWorkspacesOptions.OPT_NAME_FILTER, null);
      if ((nameSelector != null) && (nameSelector.length() > 0)) {
        workspaceName = nameSelector;
        workspaceNameKind = "partial ignorecase";
      }
    }
    
    if (subargs.hasOption(ListStreamsOptions.OPT_VISIBILITY)) {
      setVisibilityOptions(parms, subargs, fetchWorkspace, repo);
    }
    


    int loopCount = maxResults / 512 + 1;
    ScmWorkspaceList result;
    for (int count = 0; count < loopCount; count++) {
      try {
        if (count == loopCount - 1)
        {
          maxResultSize = (maxResults + 1);
        } else {
          maxResultSize = maxResults;
        }
        if (count == 0)
        {
          ScmWorkspaceList result = scmService.getSearchWorkspaces(parms);
          if (result.getWorkspaces().size() == maxResults + 1) {
            workspaces.addAll(result.getWorkspaces().subList(0, result.getWorkspaces().size() - 1));
            return true;
          }
          workspaces.addAll(result.getWorkspaces());
          maxResults -= 512;
        }
        else {
          ScmWorkspace scmWorkspace = (ScmWorkspace)workspaces.get(workspaces.size() - 1);
          modifiedBefore = scmWorkspace.getDateModified().getTime();
          ScmWorkspaceList result = scmService.getSearchWorkspaces(parms);
          if (result.getWorkspaces().size() <= 0)
          {
            return false; }
          if (result.getWorkspaces().size() == maxResults + 1)
          {
            workspaces.addAll(result.getWorkspaces().subList(0, result.getWorkspaces().size() - 1));
            return true;
          }
          workspaces.addAll(result.getWorkspaces());
          maxResults -= 512;
        }
      } catch (TeamRepositoryException e) { throw StatusHelper.wrap(Messages.ListCmd_10, e, new IndentingPrintStream(config.getContext().stderr()));
      }
    }
    return false;
  }
  
  private void setVisibilityOptions(IScmRichClientRestService.ParmsSearchWorkspaces parms, ICommandLine cli, boolean isWorkspace, ITeamRepository repo) throws FileSystemException {
    ICommandLineArgument visibility = cli.getOptionValue(ListStreamsOptions.OPT_VISIBILITY);
    if (visibility.getValue().equalsIgnoreCase("public")) {
      readScopeType = new String[] { "public_scope" };
    } else if (visibility.getValue().equalsIgnoreCase("private")) {
      readScopeType = new String[] { "private_scope" };
    } else if (visibility.getValue().equalsIgnoreCase("teamarea")) {
      readScopeType = new String[] { "team_area_private_scope" };
      if (cli.hasOption(ListStreamsOptions.OPT_PROCESSAREA)) {
        ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListStreamsOptions.OPT_PROCESSAREA), config);
        SubcommandUtil.validateArgument(value, RepoUtil.ItemType.TEAMAREA);
        ITeamArea context = RepoUtil.getTeamArea(value.getItemSelector(), null, config, repo);
        if (context != null) {
          readScopeContextId = new String[] { context.getItemId().getUuidValue() };
        }
      }
    } else if (visibility.getValue().equalsIgnoreCase("projectarea")) {
      IProjectArea context = null;
      if (cli.hasOption(ListStreamsOptions.OPT_PROCESSAREA)) {
        ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListStreamsOptions.OPT_PROCESSAREA), config);
        SubcommandUtil.validateArgument(value, RepoUtil.ItemType.PROJECTAREA);
        context = (IProjectArea)RepoUtil.getProcessArea(value, RepoUtil.ItemType.PROJECTAREA, repo, config);
      }
      
      if (isWorkspace) {
        readScopeType = new String[] { "process_area_scope", "contributor_deferring_scope" };
        if (context != null) {
          readScopeContextId = new String[] { context.getItemId().getUuidValue(), context.getItemId().getUuidValue() };
        }
      } else {
        readScopeType = new String[] { "process_area_scope" };
      }
    } else if (visibility.getValue().equalsIgnoreCase("accessgroup")) {
      readScopeType = new String[] { "access_group_scope" };
      
      if (cli.hasOption(ListStreamsOptions.OPT_ACCESSGROUP)) {
        ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListStreamsOptions.OPT_ACCESSGROUP), config);
        SubcommandUtil.validateArgument(value, RepoUtil.ItemType.ACCESSGROUP);
        IAccessGroup accessGroup = RepoUtil.getAccessGroup(value, repo, config);
        if (accessGroup != null) {
          readScopeContextId = new String[] { accessGroup.getGroupContextId().getUuidValue() };
        }
      }
    } else {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ListCmdOptions_INVALIDVISIBILITY, visibility.getValue()));
    }
    
    if (readScopeContextId == null) {
      readScopeContextId = new String[readScopeType.length];
      for (int i = 0; i < readScopeContextId.length; i++) {
        readScopeContextId[i] = "";
      }
    }
  }
  

  private void printWorkspaces(List<ScmWorkspace> workspaces, ITeamRepository repo, boolean verbose, IFilesystemRestClient client, IScmClientConfiguration config, boolean hasMoreItems)
    throws FileSystemException
  {
    JSONArray jWrkspcs = jsonizeWorkspaces(workspaces, repo, verbose, client, config);
    
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jWrkspcs);
      return;
    }
    
    if (jWrkspcs.size() == 0) {
      return;
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if (verbose)
    {
      for (Object obj : jWrkspcs)
      {
        JSONObject jWS = (JSONObject)obj;
        
        String name = (String)jWS.get("name");
        String desc = (String)jWS.get("desc");
        UUID handle = UUID.valueOf((String)jWS.get("uuid"));
        String url = (String)jWS.get("url");
        String owner = (String)jWS.get("owner");
        RepoUtil.ItemType itemType = PendingChangesUtil.getWorkspaceType((String)jWS.get("type"));
        String streamLockOwner = JSONPrintUtil.getLockOwnerName(jWS);
        String selector = AliasUtil.selector(config.getAliasConfig(), name, handle, url, itemType);
        
        if ((desc == null) || (desc.length() == 0)) {
          if (streamLockOwner == null) {
            out.println(NLS.bind(Messages.ListCmd_Workspace_Header, selector, owner));
          } else {
            out.println(NLS.bind(Messages.ListCmd_Workspace_Header_With_Lock_Owner, new String[] { selector, streamLockOwner, owner }));
          }
        } else {
          String sanitizedDescription = SubcommandUtil.sanitizeText(desc, desc.length(), false);
          if (streamLockOwner == null) {
            out.println(NLS.bind(Messages.ListCmd_Workspace_Header2, new String[] { selector, owner, sanitizedDescription }));
          } else {
            out.println(NLS.bind(Messages.ListCmd_Workspace_Header2_With_Lock_Owner, new String[] { selector, streamLockOwner, owner, sanitizedDescription }));
          }
        }
        
        JSONArray comps = (JSONArray)jWS.get("components");
        
        if (comps != null)
        {


          IndentingPrintStream verboseOut = out.indent();
          
          for (Object jobj : comps)
          {
            JSONObject jComp = (JSONObject)jobj;
            
            String output = AliasUtil.selector(config.getAliasConfig(), (String)jComp.get("name"), 
              UUID.valueOf((String)jComp.get("uuid")), (String)jComp.get("url"), RepoUtil.ItemType.COMPONENT);
            
            String lockedby = JSONPrintUtil.getLockOwnerName(jComp);
            if (lockedby != null) {
              verboseOut.println(NLS.bind(Messages.ListCmd_2, output, lockedby));
            } else {
              verboseOut.println(output);
            }
          }
        }
      }
    } else {
      for (Object obj : jWrkspcs)
      {
        JSONObject jWS = (JSONObject)obj;
        String name = (String)jWS.get("name");
        UUID handle = UUID.valueOf((String)jWS.get("uuid"));
        String url = (String)jWS.get("url");
        RepoUtil.ItemType itemType = PendingChangesUtil.getWorkspaceType((String)jWS.get("type"));
        String owner = (String)jWS.get("owner");
        String lockOwner = JSONPrintUtil.getLockOwnerName(jWS);
        String selector = AliasUtil.selector(config.getAliasConfig(), name, handle, url, itemType);
        
        if (lockOwner == null) {
          out.println(NLS.bind(Messages.ListCmd_Workspace_Header, selector, owner));
        } else {
          out.println(NLS.bind(Messages.ListCmd_Workspace_Header_With_Lock_Owner, new String[] { selector, lockOwner, owner }));
        }
      }
    }
    

    if ((!config.isJSONEnabled()) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (hasMoreItems)) {
      config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  

  private JSONArray jsonizeWorkspaces(List<ScmWorkspace> workspaces, ITeamRepository repo, boolean verbose, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Collections.sort(workspaces, new WorkspaceComparator(null));
    
    Map<String, String> ownerIdToName = new HashMap();
    
    JSONArray jWrkspcs = new JSONArray();
    List<WorkspaceDetailsDTO> wsDetails;
    if (verbose)
    {
      List<ParmsWorkspace> wsList = new ArrayList(workspaces.size());
      ParmsWorkspace parmsWs; for (ScmWorkspace ws : workspaces) {
        parmsWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId());
        wsList.add(parmsWs);
      }
      
      wsDetails = RepoUtil.getWorkspaceDetails(wsList, client, config);
      
      for (WorkspaceDetailsDTO wsDetail : wsDetails)
      {
        String ownerName = (String)ownerIdToName.get(wsDetail.getOwner().getItemId().getUuidValue());
        if (ownerName == null) {
          ownerName = RepoUtil.getOwnerName(wsDetail.getOwner(), repo, config);
          ownerIdToName.put(wsDetail.getOwner().getItemId().getUuidValue(), ownerName);
        }
        
        JSONObject jWS = JSONPrintUtil.jsonize(wsDetail.getName(), wsDetail.getItemId(), wsDetail.getRepositoryURL(), wsDetail.isStream(), ownerName);
        JSONPrintUtil.addJsonizedLockOwner(jWS, wsDetail.getLockedBy());
        String desc = wsDetail.getDescription();
        if ((desc != null) && (desc.length() > 0)) {
          jWS.put("desc", SubcommandUtil.sanitizeText(desc, desc.length(), false));
        }
        
        JSONArray components = new JSONArray();
        
        for (WorkspaceComponentDTO comp : wsDetail.getComponents())
        {
          JSONObject jComp = JSONPrintUtil.jsonize(comp.getName(), comp.getItemId(), wsDetail.getRepositoryURL());
          JSONPrintUtil.addJsonizedLockOwner(jComp, comp.getLockedBy());
          
          components.add(jComp);
        }
        jWS.put("components", components);
        
        jWrkspcs.add(jWS);
      }
    } else {
      for (ScmWorkspace ws : workspaces)
      {
        String ownerName = (String)ownerIdToName.get(ws.getOwner().getItemId());
        if (ownerName == null) {
          ownerName = RepoUtil.getOwnerName(ws.getOwner().getItemId(), ws.getOwner().getItemType(), repo, config);
          ownerIdToName.put(ws.getOwner().getItemId(), ownerName);
        }
        
        JSONObject jWS = JSONPrintUtil.jsonize(ws.getName(), ws.getItemId(), repo.getRepositoryURI(), ws.isStream(), ownerName);
        if ((jWS instanceof ScmWorkspace2)) {
          ScmWorkspace2 ws2 = (ScmWorkspace2)jWS;
          JSONPrintUtil.addJsonizedLockOwner(jWS, ws2.getLockOwner());
        }
        
        jWrkspcs.add(jWS);
      }
    }
    
    return jWrkspcs;
  }
}
