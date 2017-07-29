package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ComponentHierarchyDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineHandle;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmComponent2List;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetComponents;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetComponentsByOwner;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;










public class ListComponentsCmd
  extends AbstractSubcommand
{
  public static int DEFAULT_MAX_COMPONENTS = 25;
  
  public ListComponentsCmd() {}
  
  public void run() throws FileSystemException { ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    IScmCommandLineArgument selector = ScmCommandLineArgument.create(cli.getOptionValue(ListComponentsOptions.OPT_SELECTOR, null), config);
    boolean isSnapshot = cli.hasOption(CommonOptions.OPT_ISSNAPSHOT);
    boolean isCompName = cli.hasOption(ListComponentsOptions.OPT_NAME_FILTER);
    

    if ((isCompName) && (selector == null)) {
      throw StatusHelper.argSyntax(Messages.ListComponentsCmd_SPECIFY_COMP_NAME_NOT_SPECIFIED);
    }
    
    if ((isSnapshot) && (selector == null)) {
      throw StatusHelper.argSyntax(Messages.ListComponentsCmd_SNAPSHOT_NOT_SPECIFIED);
    }
    
    if ((isCompName) && (isSnapshot)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ListComponentsCmd_MUTUALLYEXCLUSIVE_COMPNAME_SNAPSHOT, 
        cli.getDefinition().getOption(ListComponentsOptions.OPT_NAME_FILTER).getName(), 
        cli.getDefinition().getOption(CommonOptions.OPT_ISSNAPSHOT).getName()));
    }
    
    if ((cli.hasOption(ListComponentsOptions.OPT_CONTRIB)) && ((cli.hasOption(ListComponentsOptions.OPT_PROJECTAREA)) || (cli.hasOption(ListComponentsOptions.OPT_TEAMAREA)))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ListComponentsCmd_MUTUALLYEXCLUSIVE_CONTRIB_TEAMAREA_PROJECTAREA, 
        new Object[] {
        cli.getDefinition().getOption(ListComponentsOptions.OPT_CONTRIB).getName(), 
        cli.getDefinition().getOption(ListComponentsOptions.OPT_PROJECTAREA).getName(), 
        cli.getDefinition().getOption(ListComponentsOptions.OPT_TEAMAREA).getName() }));
    }
    if ((cli.hasOption(ListComponentsOptions.OPT_PROJECTAREA)) && (cli.hasOption(ListComponentsOptions.OPT_TEAMAREA))) {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        cli.getDefinition().getOption(ListComponentsOptions.OPT_PROJECTAREA).getName(), 
        cli.getDefinition().getOption(ListComponentsOptions.OPT_TEAMAREA).getName()));
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    if ((selector == null) || (isCompName)) {
      repo = RepoUtil.loginUrlArgAnc(config, client);
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, selector);
    }
    
    boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
    
    if (isSnapshot) {
      SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.SNAPSHOT);
      listComponentsForSnapshot(repo, client, cli, selector, config, verbose);
    } else if ((isCompName) && (selector != null)) {
      listComponentsInRepo(selector.getItemSelector(), repo, cli, client, config, verbose);
    } else if (selector != null) {
      SubcommandUtil.validateArgument(selector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      listComponentsForWorkspace(repo, client, cli, selector, config, verbose);
    } else {
      listComponentsInRepo("", repo, cli, client, config, verbose);
    }
  }
  
  private static void listComponentsForSnapshot(ITeamRepository repo, IFilesystemRestClient client, ICommandLine cli, IScmCommandLineArgument selector, IScmClientConfiguration config, boolean verbose) throws FileSystemException
  {
    IBaselineSet baselineSet = RepoUtil.getSnapshot(null, selector.getItemSelector(), repo, config);
    
    int maxResults = cli.hasOption(CommonOptions.OPT_ALL) ? Integer.MAX_VALUE : RepoUtil.getMaxResultsOption(cli, DEFAULT_MAX_COMPONENTS);
    

    List<String> blIdList = new ArrayList(baselineSet.getBaselines().size());
    boolean hasMore = false;
    for (IBaselineHandle baselineHandle : baselineSet.getBaselines()) {
      if (blIdList.size() >= maxResults) {
        hasMore = true;
        break;
      }
      blIdList.add(baselineHandle.getItemId().getUuidValue());
    }
    
    List<BaselineDTO> blDTOList = RepoUtil.getBaselinesById(blIdList, repo.getRepositoryURI(), client, config);
    
    Object componentToBaseline = new HashMap();
    for (BaselineDTO baselineDTO : blDTOList) {
      ((Map)componentToBaseline).put(baselineDTO.getComponentItemId(), baselineDTO);
    }
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    IScmRichClientRestService.ParmsGetComponents parmsGetComps = new IScmRichClientRestService.ParmsGetComponents();
    componentItemIds = ((String[])((Map)componentToBaseline).keySet().toArray(new String[((Map)componentToBaseline).keySet().size()]));
    
    ScmComponent2List componentList = null;
    try {
      componentList = scmService.getComponents2(parmsGetComps);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.Common_COMPS_NOT_FOUND, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    

    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    if ((componentList.getComponents().size() == 0) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ListComponentsCmd_NO_COMPONENTS_FOUND);
      return;
    }
    
    jsonizePrintComponents(baselineSet, componentList.getComponents(), repo, (Map)componentToBaseline, printStream.indent(), verbose, config);
    
    if ((!config.isJSONEnabled()) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (hasMore)) {
      printStream.println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  


  private static void jsonizePrintComponents(IBaselineSet baselineSet, List<ScmComponent2> compList, ITeamRepository repo, Map<String, BaselineDTO> componentToBaseline, IndentingPrintStream compPrintStream, boolean verbose, IScmClientConfiguration config)
  {
    JSONObject snapshot = JSONPrintUtil.jsonize(baselineSet.getName(), baselineSet.getItemId().getUuidValue(), repo.getRepositoryURI());
    
    JSONArray comps = new JSONArray();
    
    snapshot.put("components", comps);
    
    jsonizeComponentsHelper(comps, compList, repo, componentToBaseline, compPrintStream, verbose, config);
    
    printResults(snapshot, config, verbose);
  }
  
  private static void jsonizeComponentsHelper(JSONArray comps, List<ScmComponent2> compList, ITeamRepository repo, Map<String, BaselineDTO> componentToBaseline, IndentingPrintStream compPrintStream, boolean verbose, IScmClientConfiguration config)
  {
    List<ScmComponent2> sortedCompList = new ArrayList(compList);
    Collections.sort(sortedCompList, new Comparator() {
      public int compare(ScmComponent2 o1, ScmComponent2 o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (ScmComponent2 component : sortedCompList)
    {
      JSONObject comp = JSONPrintUtil.jsonize(component.getName(), component.getItemId(), repo.getRepositoryURI());
      

      if (verbose) {
        BaselineDTO baseline = (BaselineDTO)componentToBaseline.get(component.getItemId());
        
        JSONObject bl = JSONPrintUtil.jsonize(baseline.getId(), baseline.getName(), baseline.getItemId(), repo.getRepositoryURI());
        comp.put("baseline", bl);
      }
      

      comps.add(comp);
    }
  }
  

  private static void printResults(JSONObject jSnpshot, IScmClientConfiguration config, boolean verbose)
  {
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jSnpshot);
      return;
    }
    

    IndentingPrintStream snapshotPS = new IndentingPrintStream(config.getContext().stdout());
    IndentingPrintStream componentPS = snapshotPS.indent();
    
    snapshotPS.println(NLS.bind(Messages.ListComponentsCmd_Snapshot, 
      AliasUtil.selector(
      (String)jSnpshot.get("name"), UUID.valueOf((String)jSnpshot.get("uuid")), (String)jSnpshot.get("url"), RepoUtil.ItemType.SNAPSHOT)));
    
    JSONArray jComps = (JSONArray)jSnpshot.get("components");
    
    printComponents(jComps, config, componentPS, verbose);
  }
  

  private static void printComponents(JSONArray jComps, IScmClientConfiguration config, IndentingPrintStream componentPS, boolean verbose)
  {
    if (config.isJSONEnabled()) {
      JSONObject root = new JSONObject();
      root.put("components", jComps);
      config.getContext().stdout().print(root);
      return;
    }
    
    IndentingPrintStream baselinePS = componentPS.indent();
    
    for (Object obj : jComps)
    {
      JSONObject comp = (JSONObject)obj;
      
      if (verbose) {
        componentPS.println(NLS.bind(Messages.ListComponentsCmd_Component, 
          AliasUtil.selector((String)comp.get("name"), UUID.valueOf((String)comp.get("uuid")), (String)comp.get("url"), RepoUtil.ItemType.COMPONENT)));
      } else {
        componentPS.println(AliasUtil.selector((String)comp.get("name"), UUID.valueOf((String)comp.get("uuid")), (String)comp.get("url"), RepoUtil.ItemType.COMPONENT));
      }
      
      JSONObject bl = (JSONObject)comp.get("baseline");
      
      if (bl != null) {
        baselinePS.println(NLS.bind(Messages.ListComponentsCmd_Baseline, 
          AliasUtil.selector(((Integer)bl.get("id")).intValue(), (String)bl.get("name"), UUID.valueOf((String)bl.get("uuid")), (String)bl.get("url"), RepoUtil.ItemType.BASELINE)));
      }
    }
  }
  

  private static void listComponentsForWorkspace(ITeamRepository repo, IFilesystemRestClient client, ICommandLine cli, IScmCommandLineArgument wsSelector, IScmClientConfiguration config, boolean verbose)
    throws FileSystemException
  {
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(0);
    options.enablePrinter(1);
    options.enablePrinter(38);
    options.enablePrinter(24);
    options.enablePrinter(25);
    if (verbose) {
      options.enablePrinter(11);
    }
    int maxResults = cli.hasOption(CommonOptions.OPT_ALL) ? Integer.MAX_VALUE : RepoUtil.getMaxResultsOption(cli, DEFAULT_MAX_COMPONENTS);
    options.setMaxComponents(maxResults);
    

    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    options.isInFilter(wsFound.getItemId(), 0);
    

    WorkspaceDetailsDTO wsDetails = getWorkspaceDetails(repo, wsFound, client, config);
    

    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    if ((wsDetails.getComponents().size() == 0) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ListComponentsCmd_NO_COMPONENTS_FOUND);
      return;
    }
    

    WorkspaceDetailsDTO wsIncomingFlowDetails = getTargetFlowDetails(wsDetails, true, client, config);
    WorkspaceDetailsDTO wsOutgoingFlowDetails = getTargetFlowDetails(wsDetails, false, client, config);
    

    JSONObject workspaceJson = JSONPrintUtil.jsonizeWorkspace(wsDetails, 
      wsIncomingFlowDetails, wsOutgoingFlowDetails, repo, options);
    


    JSONArray wses = new JSONArray();
    if (workspaceJson.size() > 0) {
      wses.add(workspaceJson);
    }
    

    if (config.isJSONEnabled()) {
      JSONObject root = new JSONObject();
      root.put("workspaces", wses);
      config.getContext().stdout().print(root);
    } else {
      PendingChangesUtil.printWorkspaces(wses, options, printStream);
      if (!cli.hasOption(CommonOptions.OPT_MAXRESULTS))
      {
        if (hasMoreComponents(wsDetails, maxResults, options)) {
          printStream.println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
        }
      }
    }
  }
  
  private static boolean hasMoreComponents(WorkspaceDetailsDTO workspace, int max, PendingChangesUtil.PendingChangesOptions options) {
    int sum = 0;
    List<WorkspaceComponentDTO> componentDtos = workspace.getComponents();
    if (componentDtos.size() >= max) {
      return true;
    }
    List<ComponentHierarchyDTO> componentHierarchyDtos = workspace.getComponentHierarchies();
    
    if (options.isPrinterEnabled(38))
    {
      componentHierarchyDtos = new ArrayList(componentHierarchyDtos);
      
      for (ComponentHierarchyDTO componentHieararchyDto : componentHierarchyDtos) {
        if (options.isInFilter(UUID.valueOf(componentHieararchyDto.getComponentItemId()), 1))
        {

          if (componentHieararchyDto.isRoot()) {
            if (sum >= max) {
              return true;
            }
            sum++;
          } }
      }
    } else {
      for (WorkspaceComponentDTO componentDto : componentDtos)
        if (options.isInFilter(UUID.valueOf(componentDto.getItemId()), 1))
        {

          if (sum >= max) {
            return true;
          }
          sum++;
        }
    }
    if (sum >= max) {
      return true;
    }
    return false;
  }
  

  private static WorkspaceDetailsDTO getWorkspaceDetails(ITeamRepository repo, IWorkspace wsFound, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsGetWorkspace parms = new ParmsGetWorkspace();
    includeComponents = Boolean.valueOf(true);
    includeComponentHierarchy = Boolean.valueOf(true);
    includeFlowTargets = Boolean.valueOf(true);
    refresh = Boolean.valueOf(true);
    workspace = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    ParmsGetWorkspace[] workspaces = { parms };
    
    List<WorkspaceDetailsDTO> workspaceDetailsDtos = RepoUtil.getWorkspaceDetails(workspaces, client, config);
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)workspaceDetailsDtos.get(0);
    return wsDetails;
  }
  









  private static WorkspaceDetailsDTO getTargetFlowDetails(WorkspaceDetailsDTO wsDetails, boolean incoming, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    WorkspaceDetailsDTO wsFlowDetails = null;
    ParmsWorkspace targetFlowItem = RepoUtil.getFlowTarget(wsDetails, incoming, true);
    

    if (targetFlowItem != null) {
      ITeamRepository targetFlowRepo = null;
      try {
        targetFlowRepo = RepoUtil.getSharedRepository(repositoryUrl, true);
      }
      catch (IllegalArgumentException localIllegalArgumentException) {}
      



      if (targetFlowRepo != null) {
        wsFlowDetails = getTargetFlowDetails(client, config, targetFlowItem);
      }
    }
    return wsFlowDetails;
  }
  
  private void listComponentsInRepo(String compName, ITeamRepository repo, ICommandLine cli, IFilesystemRestClient client, IScmClientConfiguration config, boolean verbose) throws FileSystemException
  {
    String projectAreaSelector = cli.getOption(ListComponentsOptions.OPT_PROJECTAREA, null);
    String teamAreaSelector = cli.getOption(ListComponentsOptions.OPT_TEAMAREA, null);
    String contribName = cli.getOption(ListComponentsOptions.OPT_CONTRIB, null);
    

    IProjectArea projectArea = null;
    if ((projectAreaSelector != null) && (projectAreaSelector.length() != 0))
    {
      projectArea = RepoUtil.getProjectArea(repo, projectAreaSelector, config);
      
      if (projectArea == null) {
        throw StatusHelper.failure(NLS.bind(Messages.ListCmd_NOPROJECTAREA, projectAreaSelector), null);
      }
    }
    
    IContributor contributor = null;
    if ((contribName != null) && (contribName.length() > 0)) {
      contributor = RepoUtil.getContributor(contribName, repo, config);
    }
    
    ITeamArea teamArea = RepoUtil.getTeamArea(teamAreaSelector, projectArea, config, repo);
    if ((teamArea == null) && (teamAreaSelector != null)) {
      config.getContext().stderr().println(NLS.bind(Messages.ListCmd_TeamAreaNotFound, teamAreaSelector));
      return;
    }
    
    listComponentsByName(compName, projectArea, teamArea, contributor, client, repo, config, verbose, cli);
  }
  

  private void listComponentsByName(String compName, IProjectArea projectArea, ITeamArea teamArea, IContributor contributor, IFilesystemRestClient client, ITeamRepository repo, IScmClientConfiguration config, boolean verbose, ICommandLine cli)
    throws FileSystemException
  {
    int maxResults = cli.hasOption(CommonOptions.OPT_ALL) ? Integer.MAX_VALUE : RepoUtil.getMaxResultsOption(cli, DEFAULT_MAX_COMPONENTS);
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    IScmRichClientRestService.ParmsGetComponentsByOwner parms = new IScmRichClientRestService.ParmsGetComponentsByOwner();
    IAuditableHandle owner = null;
    if (teamArea != null) {
      owner = teamArea;
    } else if (contributor != null) {
      owner = contributor;
    } else {
      owner = projectArea;
    }
    
    if (owner != null) {
      ownerItemId = owner.getItemId().getUuidValue();
      ownerItemType = owner.getItemType().getName();
      ownerItemNamespace = owner.getItemType().getNamespaceURI();
    }
    
    if (cli.hasOption(ListComponentsOptions.OPT_VISIBILITY)) {
      ICommandLineArgument visibility = cli.getOptionValue(ListComponentsOptions.OPT_VISIBILITY);
      if (visibility.getValue().equalsIgnoreCase("public")) {
        readScopeType = "public_scope";
      } else if (visibility.getValue().equalsIgnoreCase("private")) {
        readScopeType = "private_scope";
      } else if (visibility.getValue().equalsIgnoreCase("teamarea")) {
        readScopeType = "team_area_private_scope";
        if (cli.hasOption(ListComponentsOptions.OPT_PROCESSAREA)) {
          ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListComponentsOptions.OPT_PROCESSAREA), config);
          SubcommandUtil.validateArgument(value, RepoUtil.ItemType.TEAMAREA);
          ITeamArea context = RepoUtil.getTeamArea(value.getItemSelector(), null, config, repo);
          if (context != null) {
            readScopeContextId = context.getItemId().getUuidValue();
          } else {
            throw StatusHelper.argSyntax(NLS.bind(Messages.QueryBuilder_NoSuchTeamArea, value.getItemSelector()));
          }
        } else {
          readScopeContextId = "";
        }
      } else if (visibility.getValue().equalsIgnoreCase("projectarea")) {
        IProjectArea context = null;
        readScopeType = "deferring_project";
        
        if (cli.hasOption(ListComponentsOptions.OPT_PROCESSAREA)) {
          ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListComponentsOptions.OPT_PROCESSAREA), config);
          SubcommandUtil.validateArgument(value, RepoUtil.ItemType.PROJECTAREA);
          context = (IProjectArea)RepoUtil.getProcessArea(value, RepoUtil.ItemType.PROJECTAREA, repo, config);
          if (context != null) {
            readScopeContextId = context.getItemId().getUuidValue();
          } else {
            throw StatusHelper.argSyntax(NLS.bind(Messages.QueryBuilder_NoSuchProjectArea, value.getItemSelector()));
          }
        } else {
          readScopeContextId = "";
        }
      } else if (visibility.getValue().equalsIgnoreCase("accessgroup")) {
        readScopeType = "access_group_scope";
        
        if (cli.hasOption(ListComponentsOptions.OPT_ACCESSGROUP)) {
          ScmCommandLineArgument value = ScmCommandLineArgument.create(cli.getOptionValue(ListComponentsOptions.OPT_ACCESSGROUP), config);
          SubcommandUtil.validateArgument(value, RepoUtil.ItemType.ACCESSGROUP);
          IAccessGroup accessGroup = RepoUtil.getAccessGroup(value, repo, config);
          if (accessGroup != null) {
            readScopeContextId = accessGroup.getGroupContextId().getUuidValue();
          } else {
            throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_ACCESS_GROUP, value.getItemSelector()));
          }
        } else {
          readScopeContextId = "";
        }
      }
    }
    

    List<ScmComponent2> compList = new ArrayList();
    ScmComponent2List componentList = null;
    long time = 0L;
    boolean fetch = true;
    do {
      if (time != 0L) {
        lastComponentModifiedDate = time;
      }
      try
      {
        componentList = scmService.getSearchComponents2(parms);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.Common_COMPS_NOT_FOUND, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
      
      if (componentList.getComponents().size() == 0) {
        fetch = false;
      }
      
      ScmComponent2 lastComp = null;
      for (ScmComponent2 comp : componentList.getComponents())
      {
        if ((compName == null) || (compName.length() == 0) || (comp.getName().startsWith(compName))) {
          compList.add(comp);
          
          if (compList.size() == maxResults) {
            fetch = false;
            break;
          }
        }
        lastComp = comp;
      }
      

      if (lastComp != null) {
        time = lastComp.getDateModified().getTime();
      }
    } while (
    































      fetch);
    
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    if ((compList.size() == 0) && (!config.isJSONEnabled())) {
      printStream.println(Messages.ListComponentsCmd_NO_COMPONENTS_FOUND);
      return;
    }
    

    Map<String, BaselineDTO> compToBaseline = null;
    if (verbose) {
      compToBaseline = getBaselines(compList, repo, client, config);
    }
    
    JSONArray jComps = new JSONArray();
    

    jsonizeComponentsHelper(jComps, compList, repo, compToBaseline, printStream, verbose, config);
    
    printComponents(jComps, config, new IndentingPrintStream(config.getContext().stdout()), verbose);
    
    if ((!config.isJSONEnabled()) && (!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (compList.size() >= maxResults)) {
      printStream.println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
    }
  }
  
  private Map<String, BaselineDTO> getBaselines(List<ScmComponent2> components, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<String, BaselineDTO> compToBaseline = new HashMap();
    Iterator localIterator2;
    for (Iterator localIterator1 = components.iterator(); localIterator1.hasNext(); 
        
        localIterator2.hasNext())
    {
      ScmComponent2 comp = (ScmComponent2)localIterator1.next();
      List<BaselineDTO> blDTO = RepoUtil.findBaselines(null, comp.getItemId(), 0, repo.getRepositoryURI(), client, config, comp.getName());
      localIterator2 = blDTO.iterator(); continue;BaselineDTO baselineDTO = (BaselineDTO)localIterator2.next();
      compToBaseline.put(baselineDTO.getComponentItemId(), baselineDTO);
    }
    

    return compToBaseline;
  }
  








  private static WorkspaceDetailsDTO getTargetFlowDetails(IFilesystemRestClient client, IScmClientConfiguration config, ParmsWorkspace targetFlowItem)
    throws FileSystemException
  {
    ParmsWorkspace workspace = new ParmsWorkspace(repositoryUrl, workspaceItemId);
    ParmsGetWorkspace parms = new ParmsGetWorkspace(workspace, false, false);
    refresh = Boolean.valueOf(false);
    WorkspaceDetailsDTO wsFlowDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(new ParmsGetWorkspace[] { parms }, client, config).get(0);
    return wsFlowDetails;
  }
}
