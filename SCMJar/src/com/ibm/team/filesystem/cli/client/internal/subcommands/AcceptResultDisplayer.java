package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.JSONPatchPrinter;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.PortsUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsContext;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResolveChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceAcceptDetailed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdate;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.DeliverResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredBaselineUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredComponentUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.WorkspaceUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConnectionDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItem;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;


public class AcceptResultDisplayer
{
  public AcceptResultDisplayer() {}
  
  private static final class JsonNameComparator
    implements Comparator<JSONObject>
  {
    private JsonNameComparator() {}
    
    public int compare(JSONObject object1, JSONObject object2)
    {
      return ((String)object1.get("name")).compareTo((String)object2.get("name"));
    }
  }
  
  static enum WarningType {
    ADD, 
    DELETE, 
    REPLACE;
  }
  
  public static void showResult(IFilesystemRestClient client, ITeamRepository repo, boolean flowedComponents, ParmsWorkspaceUpdate parms, WorkspaceUpdateResultDTO result, List<ShareableDTO> deletedContentAutoMergeShareables, boolean verbose, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jResult = new JSONObject();
    JSONArray jRepos = jsonizeResults(client, result.getStructuredResult(), verbose, config);
    
    if (jRepos.size() > 0) {
      jResult.put("repos", jRepos);
    }
    

    HashMap<String, Map<String, Map<String, WarningType>>> warnings = populateWarnings(result);
    
    JSONArray jNonFlowedComps = null;
    if ((!flowedComponents) && (warnings.size() > 0)) {
      jNonFlowedComps = jsonizeRemainingComponentOperations(repo, warnings, parms, client, config);
      jResult.put("non-flowed-comps", jNonFlowedComps);
    }
    

    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jResult);
      return;
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    showResults(jRepos, verbose, config, out.indent());
    
    showRemainingComponentOperations(jNonFlowedComps, out, config);
    
    if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
      SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
    }
    
    if ((deletedContentAutoMergeShareables != null) && (deletedContentAutoMergeShareables.size() > 0)) {
      SubcommandUtil.showDeletedContent(Messages.AcceptResultDisplayer_DELETED_CONTENT_WHEN_AUTOMERGING, 
        deletedContentAutoMergeShareables, out);
    }
    
    if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
    }
    
    if (result.getStructuredResult().size() == 0) {
      out.println(Messages.AcceptResultDisplayer_WORKSPACE_UNCHANGED);
    }
  }
  
  public static JSONArray jsonizeResults(IFilesystemRestClient client, List<StructuredUpdateReportDTO> result, boolean verbose, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<String, List<StructuredUpdateReportDTO>> repos = new HashMap();
    List<StructuredUpdateReportDTO> reports;
    for (StructuredUpdateReportDTO report : result) {
      reports = (List)repos.get(report.getRepositoryUrl());
      
      if (reports == null) {
        reports = new ArrayList(result.size());
        repos.put(report.getRepositoryUrl(), reports);
      }
      
      reports.add(report);
    }
    
    JSONArray jRepos = new JSONArray();
    

    for (Object reports : repos.values()) {
      boolean showRepo = true;
      
      JSONObject jRepo = new JSONObject();
      JSONArray jWorkspaces = new JSONArray();
      
      for (StructuredUpdateReportDTO report : (List)reports)
      {
        if (showRepo) {
          String repoUrl = report.getRepositoryUrl();
          
          jRepo.put("repo-url", repoUrl);
          showRepo = false;
        }
        
        jsonizeWorkspaceUpdateReport(jWorkspaces, client, report, verbose, config);
      }
      
      jRepo.put("workspaces", jWorkspaces);
      
      jRepos.add(jRepo);
    }
    
    return jRepos;
  }
  
  public static void showResult(IFilesystemRestClient client, DeliverResultDTO result, boolean verbose, IScmClientConfiguration config, IndentingPrintStream out)
    throws FileSystemException
  {
    JSONObject jResult = new JSONObject();
    
    JSONArray jRepos = jsonizeResults(client, result.getStructuredResult(), verbose, config);
    
    if (jRepos.size() > 0) {
      jResult.put("repos", jRepos);
    }
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jResult);
    } else {
      showResults(jRepos, verbose, config, out);
      
      if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
        SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
      }
      
      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
      }
    }
  }
  

  public static void showResults(JSONArray jRepos, boolean verbose, IScmClientConfiguration config, IndentingPrintStream out)
    throws FileSystemException
  {
    if (jRepos == null) {
      return;
    }
    
    Iterator localIterator2;
    for (Iterator localIterator1 = jRepos.iterator(); localIterator1.hasNext(); 
        










        localIterator2.hasNext())
    {
      Object obj = localIterator1.next();
      
      JSONObject jRepo = (JSONObject)obj;
      
      String repoUrl = (String)jRepo.get("repo-url");
      
      out.println(NLS.bind(Messages.AcceptResultDisplayer_REPOSITORY_DESCRIPTION, repoUrl));
      
      JSONArray jWorkspaces = (JSONArray)jRepo.get("workspaces");
      
      List<JSONObject> workspaceJsons = new ArrayList(jWorkspaces);
      Collections.sort(workspaceJsons, new JsonNameComparator(null));
      localIterator2 = workspaceJsons.iterator(); continue;Object jObj = localIterator2.next();
      
      JSONObject jWorkspace = (JSONObject)jObj;
      
      showWorkspaceUpdateReport(config, jWorkspace, out, verbose);
    }
  }
  


  private static JSONArray jsonizeRemainingComponentOperations(ITeamRepository repo, HashMap<String, Map<String, Map<String, WarningType>>> warnings, ParmsWorkspaceUpdate parms, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<String, ITeamRepository> comp2Repo = getComponentToRepoMapping(repo, warnings, parms, config);
    
    Map<UUID, String> names = fetchNames(repo, warnings, comp2Repo, config);
    
    JSONArray jNonFlowedComps = new JSONArray();
    
    for (Map.Entry<String, Map<String, Map<String, WarningType>>> repoEntry : warnings.entrySet())
    {
      JSONObject jRepo = new JSONObject();
      jRepo.put("repo", repoEntry.getKey());
      
      JSONArray jWorkspaces = new JSONArray();
      for (Map.Entry<String, Map<String, WarningType>> wsEntry : ((Map)repoEntry.getValue()).entrySet())
      {
        JSONObject jWS = new JSONObject();
        

        ParmsWorkspace targetWs = new ParmsWorkspace(repo.getRepositoryURI(), (String)wsEntry.getKey());
        WorkspaceDetailsDTO targetWsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
        RepoUtil.ItemType targetWsType = targetWsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE;
        
        String name = (String)names.get(UUID.valueOf((String)wsEntry.getKey()));
        jWS.put("name", name);
        jWS.put("uuid", wsEntry.getKey());
        jWS.put("type", targetWsType.toString());
        jWS.put("url", repo.getRepositoryURI());
        
        JSONArray jComps = new JSONArray();
        
        for (Map.Entry<String, WarningType> compEntry : ((Map)wsEntry.getValue()).entrySet())
        {
          JSONObject jComp = new JSONObject();
          
          name = (String)names.get(UUID.valueOf((String)compEntry.getKey()));
          jComp.put("name", name);
          
          String srcRepoUrl = ((ITeamRepository)comp2Repo.get(compEntry.getKey())).getRepositoryURI();
          
          jComp.put("url", srcRepoUrl);
          
          jComp.put("uuid", compEntry.getKey());
          
          switch ((WarningType)compEntry.getValue()) {
          case ADD: 
            jComp.put("type", "add");
            break;
          
          case DELETE: 
            jComp.put("type", "delete");
            break;
          
          case REPLACE: 
            jComp.put("type", "replace");
            break;
          

          default: 
            jComp.put("type", name);
          }
          
          
          jComps.add(jComp);
        }
        
        if (jComps.size() > 0) {
          jWS.put("components", jComps);
        }
        
        jWorkspaces.add(jWS);
      }
      
      if (jWorkspaces.size() > 0) {
        jRepo.put("workspaces", jWorkspaces);
      }
      
      jNonFlowedComps.add(jRepo);
    }
    return jNonFlowedComps;
  }
  
  private static void showRemainingComponentOperations(JSONArray jNonFlowedComps, IndentingPrintStream out, IScmClientConfiguration config)
    throws FileSystemException
  {
    if (jNonFlowedComps == null) {
      return;
    }
    
    out.println(NLS.bind(Messages.AcceptResultDisplayer_COMPONENT_CHANGE_TITLE, AcceptCmdOptions.OPT_FLOW_COMPONENTS.getName()));
    
    for (Object obj : jNonFlowedComps)
    {
      JSONObject jRepo = (JSONObject)obj;
      IndentingPrintStream repoOut = out.indent();
      
      repoOut.println((String)jRepo.get("repo"));
      
      JSONArray jWorkspaces = (JSONArray)jRepo.get("workspaces");
      
      if (jWorkspaces != null)
      {


        for (Object wsObj : jWorkspaces)
        {
          JSONObject jWS = (JSONObject)wsObj;
          IndentingPrintStream wsOut = repoOut.indent();
          
          String name = (String)jWS.get("name");
          String uuid = (String)jWS.get("uuid");
          String wsTypeStr = (String)jWS.get("type");
          String url = (String)jWS.get("url");
          
          RepoUtil.ItemType wsType = PendingChangesUtil.getWorkspaceType(wsTypeStr);
          
          wsOut.println(AliasUtil.selector(config.getAliasConfig(), name, UUID.valueOf(uuid), url, wsType));
          
          JSONArray jComps = (JSONArray)jWS.get("components");
          IndentingPrintStream compOut = wsOut.indent();
          
          if (jComps != null)
          {


            for (Object jCompObj : jComps)
            {
              JSONObject jComp = (JSONObject)jCompObj;
              
              String compUUID = (String)jComp.get("uuid");
              
              String compName = (String)jComp.get("name");
              
              String srcRepoUrl = (String)jComp.get("url");
              
              String opType = (String)jComp.get("type");
              
              if (opType.equals("add")) {
                compOut.println(NLS.bind(Messages.AcceptResultDisplayer_COMP_ADD, AliasUtil.selector(config.getAliasConfig(), compName, UUID.valueOf(compUUID), srcRepoUrl, RepoUtil.ItemType.COMPONENT)));
              }
              else if (opType.equals("delete")) {
                compOut.println(NLS.bind(Messages.AcceptResultDisplayer_COMP_DELETE, AliasUtil.selector(config.getAliasConfig(), compName, UUID.valueOf(compUUID), srcRepoUrl, RepoUtil.ItemType.COMPONENT)));
              }
              else if (opType.equals("replace")) {
                compOut.println(NLS.bind(Messages.AcceptResultDisplayer_COMP_REPLACE, AliasUtil.selector(config.getAliasConfig(), compName, UUID.valueOf(compUUID), srcRepoUrl, RepoUtil.ItemType.COMPONENT)));
              } else {
                compOut.println(NLS.bind(Messages.AcceptResultDisplayer_ERROR_MSG, compName));
              }
            }
          }
        }
      }
    }
  }
  
  private static Map<String, ITeamRepository> getComponentToRepoMapping(ITeamRepository repo, HashMap<String, Map<String, Map<String, WarningType>>> warnings, ParmsWorkspaceUpdate parms, IScmClientConfiguration config) throws CLIFileSystemClientException {
    Map<String, ITeamRepository> comp2Repo = new HashMap();
    Map<String, ITeamRepository> url2Repo = new HashMap();
    
    String componentItemId;
    if (workspaceAcceptDetailed != null) {
      for (ParmsWorkspaceAcceptDetailed acceptParms : workspaceAcceptDetailed) {
        if (componentItemIds != null) {
          ITeamRepository sourceRepo = (ITeamRepository)url2Repo.get(sourceWorkspace.repositoryUrl);
          if (sourceRepo == null) {
            sourceRepo = RepoUtil.getSharedRepository(sourceWorkspace.repositoryUrl, true);
            url2Repo.put(sourceWorkspace.repositoryUrl, sourceRepo);
          }
          
          for (componentItemId : componentItemIds) {
            comp2Repo.put(componentItemId, sourceRepo);
          }
        }
      }
    }
    

    for (Iterator localIterator1 = warnings.entrySet().iterator(); localIterator1.hasNext(); 
        ((Iterator)???).hasNext())
    {
      Map.Entry<String, Map<String, Map<String, WarningType>>> repoEntry = (Map.Entry)localIterator1.next();
      ??? = ((Map)repoEntry.getValue()).entrySet().iterator(); continue;Object wsEntry = (Map.Entry)((Iterator)???).next();
      for (Map.Entry<String, WarningType> compEntry : ((Map)((Map.Entry)wsEntry).getValue()).entrySet())
      {


        String componentUuid = (String)compEntry.getKey();
        if (compEntry.getValue() == WarningType.ADD) {
          boolean found = comp2Repo.containsKey(componentUuid);
          if (!found)
          {







            for (ITeamRepository testRepo : comp2Repo.values()) {
              IItemHandle componentHandle = IComponent.ITEM_TYPE.createItemHandle(UUID.valueOf(componentUuid), null);
              IItem component = null;
              try {
                component = testRepo.itemManager().fetchCompleteItem(componentHandle, 0, null);
              }
              catch (TeamRepositoryException localTeamRepositoryException) {}
              
              if (component != null) {
                comp2Repo.put(componentUuid, testRepo);
                found = true;
                break;
              }
            }
          }
          
          if (!found) {
            throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangeDisplayer_7, componentUuid));
          }
        } else {
          comp2Repo.put(componentUuid, repo);
        }
      }
    }
    

    return comp2Repo;
  }
  
  private static Map<UUID, String> fetchNames(ITeamRepository repo, HashMap<String, Map<String, Map<String, WarningType>>> warnings, Map<String, ITeamRepository> comp2Repo, IScmClientConfiguration config) throws FileSystemException {
    Map<ITeamRepository, Set<IItemHandle>> repo2ItemHandles = new HashMap();
    Iterator localIterator2;
    Map.Entry<String, Map<String, WarningType>> wsEntry;
    for (Iterator localIterator1 = warnings.entrySet().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      Map.Entry<String, Map<String, Map<String, WarningType>>> repoEntry = (Map.Entry)localIterator1.next();
      localIterator2 = ((Map)repoEntry.getValue()).entrySet().iterator(); continue;wsEntry = (Map.Entry)localIterator2.next();
      Set<IItemHandle> itemHandles = (Set)repo2ItemHandles.get(repo);
      if (itemHandles == null) {
        itemHandles = new HashSet();
        repo2ItemHandles.put(repo, itemHandles);
      }
      itemHandles.add(IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf((String)wsEntry.getKey()), null));
      
      for (Map.Entry<String, WarningType> compEntry : ((Map)wsEntry.getValue()).entrySet()) {
        ITeamRepository srcRepo = (ITeamRepository)comp2Repo.get(compEntry.getKey());
        Set<IItemHandle> itemCompHandles = (Set)repo2ItemHandles.get(srcRepo);
        if (itemCompHandles == null) {
          itemCompHandles = new HashSet();
          repo2ItemHandles.put(srcRepo, itemCompHandles);
        }
        itemCompHandles.add(IComponent.ITEM_TYPE.createItemHandle(UUID.valueOf((String)compEntry.getKey()), null));
      }
    }
    


    List<IItem> items = new ArrayList();
    try {
      for (Object repoEntry : repo2ItemHandles.entrySet()) {
        itemHandles = new ArrayList((Collection)((Map.Entry)repoEntry).getValue());
        List<IItem> repoItems = ((ITeamRepository)((Map.Entry)repoEntry).getKey()).itemManager().fetchCompleteItems((List)itemHandles, 0, null);
        items.addAll(repoItems);
      }
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.AcceptResultDisplayer_FETCH_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    Object names = new HashMap();
    for (Object itemHandles = items.iterator(); ((Iterator)itemHandles).hasNext();) { IItem item = (IItem)((Iterator)itemHandles).next();
      String name;
      String name; if ((item instanceof IWorkspace)) {
        name = ((IWorkspace)item).getName();
      } else { String name;
        if ((item instanceof IComponent)) {
          name = ((IComponent)item).getName();
        } else {
          name = NLS.bind(Messages.AcceptResultDisplayer_UNKNOWN_INTERNAL_CLASS, item.getClass().getSimpleName());
        }
      }
      ((HashMap)names).put(item.getItemId(), name);
    }
    
    return names;
  }
  










  private static HashMap<String, Map<String, Map<String, WarningType>>> populateWarnings(WorkspaceUpdateResultDTO result)
  {
    HashMap<String, Map<String, Map<String, WarningType>>> toReturn = new HashMap();
    
    populateWarning(result.getComponentFlowAdditions(), toReturn, WarningType.ADD);
    populateWarning(result.getComponentFlowDeletions(), toReturn, WarningType.DELETE);
    populateWarning(result.getComponentReplacementCandidates(), toReturn, WarningType.REPLACE);
    
    return toReturn;
  }
  



  private static void populateWarning(List<ConfigurationDescriptorDTO> comps, HashMap<String, Map<String, Map<String, WarningType>>> toReturn, WarningType type)
  {
    for (ConfigurationDescriptorDTO comp : comps) {
      String url = comp.getConnection().getRepositoryURL();
      
      Map<String, Map<String, WarningType>> ws = (Map)toReturn.get(url);
      if (ws == null) {
        ws = new HashMap();
        toReturn.put(url, ws);
      }
      
      String contextId = comp.getConnection().getContextItemId();
      Map<String, WarningType> containedComps = (Map)ws.get(contextId);
      if (containedComps == null) {
        containedComps = new HashMap();
        ws.put(contextId, containedComps);
      }
      
      containedComps.put(comp.getComponentItemId(), type);
    }
  }
  
  private static HashSet<String> extractConflicts(StructuredComponentUpdateReportDTO compDto) {
    HashSet<String> conflicts = new HashSet();
    conflicts.addAll(compDto.getConflictedItemIds());
    
    return conflicts;
  }
  
  private static void jsonizeWorkspaceUpdateReport(JSONArray jWorkspaces, IFilesystemRestClient client, StructuredUpdateReportDTO dto, boolean verbose, IScmClientConfiguration config)
    throws FileSystemException
  {
    JSONObject jWS = new JSONObject();
    
    jWorkspaces.add(jWS);
    
    jWS.put("name", dto.getWorkspaceName());
    jWS.put("uuid", dto.getWorkspaceItemId());
    jWS.put("url", dto.getRepositoryUrl());
    
    JSONArray jComps = new JSONArray();
    

    for (StructuredComponentUpdateReportDTO compDto : dto.getComponents())
    {
      JSONObject jComp = new JSONObject();
      jComp.put("name", compDto.getComponentName());
      jComp.put("uuid", compDto.getComponentItemId());
      jComp.put("url", dto.getRepositoryUrl());
      Set<String> conflictIds = extractConflicts(compDto);
      

      CurrentPatchDTO currentPort = compDto.getCurrentPatch();
      if (currentPort != null) {
        PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
        options.enablePrinter(4);
        options.setVerbose((verbose) || (config.isJSONEnabled()));
        
        JSONPatchPrinter portChangeSets = PortsUtil.getChangeSets(dto.getRepositoryUrl(), currentPort, client, config);
        portChangeSets.jsonize(jComp, options, config);
      }
      

      List<ChangeSetSyncDTO> csDtos = getChangeSets(client, dto.getRepositoryUrl(), 
        dto.getWorkspaceItemId(), compDto.getChangeSetItemIds(), config);
      if (!csDtos.isEmpty()) {
        PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
        options.enablePrinter(4);
        options.enablePrinter(23);
        options.enablePrinter(6);
        options.enablePrinter(7);
        options.setVerbose((verbose) || (config.isJSONEnabled()));
        
        ChangeSetStateFactory stateFactory = new ChangeSetStateFactory();
        stateFactory.setConflictItems(new ArrayList(conflictIds));
        JSONPrintUtil.jsonizeChangeSets(jComp, "changes", csDtos, stateFactory, options, client, config);
      }
      

      List<StructuredBaselineUpdateReportDTO> baselineDtos = compDto.getBaselines();
      if (!baselineDtos.isEmpty()) {
        JSONArray jBaselines = new JSONArray();
        jsonizeBaselines(jBaselines, baselineDtos, dto.getRepositoryUrl());
        jComp.put("baselines", jBaselines);
      }
      jComps.add(jComp);
    }
    
    if (jComps.size() > 0) {
      jWS.put("components", jComps);
    }
  }
  
  private static void showWorkspaceUpdateReport(IScmClientConfiguration config, JSONObject jWorkspace, IndentingPrintStream out, boolean verbose)
    throws FileSystemException
  {
    out.println(NLS.bind(Messages.AcceptResultDisplayer_WORKSPACE_DESCRIPTION, 
      AliasUtil.selector((String)jWorkspace.get("name"), 
      UUID.valueOf((String)jWorkspace.get("uuid")), 
      (String)jWorkspace.get("url"), 
      RepoUtil.ItemType.WORKSPACE)));
    



    out = out.indent();
    
    JSONArray jComps = (JSONArray)jWorkspace.get("components");
    

    if (jComps != null) {
      List<JSONObject> componentJsons = new ArrayList(jComps);
      Collections.sort(componentJsons, new JsonNameComparator(null));
      for (JSONObject jComp : componentJsons)
      {
        out.println(NLS.bind(Messages.AcceptResultDisplayer_COMPONENT_DESCRIPTION, 
          AliasUtil.selector((String)jComp.get("name"), 
          UUID.valueOf((String)jComp.get("uuid")), 
          (String)jComp.get("url"), 
          RepoUtil.ItemType.COMPONENT)));
        



        IndentingPrintStream compOut = out.indent();
        

        PendingChangesUtil.PendingChangesOptions pOptions = new PendingChangesUtil.PendingChangesOptions();
        pOptions.enablePrinter(4);
        pOptions.enablePrinter(32);
        pOptions.setVerbose((verbose) || (config.isJSONEnabled()));
        PendingChangesUtil.printCurrentPort((String)jComp.get("url"), jComp, pOptions, compOut);
        
        if (jComp.get("changes") != null) {
          PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
          options.enablePrinter(4);
          options.enablePrinter(23);
          options.enablePrinter(6);
          options.enablePrinter(7);
          options.setVerbose((verbose) || (config.isJSONEnabled()));
          PendingChangesUtil.printChangeSets(jComp, "changes", options, compOut);
        }
        

        JSONArray jBaselines = (JSONArray)jComp.get("baselines");
        
        if (jBaselines != null)
        {
          compOut.println(Messages.AcceptResultDisplayer_LIST_OF_ACCEPTED_BASELINES);
          
          IndentingPrintStream blOut = compOut.indent();
          showBaselines(jBaselines, blOut, (String)jComp.get("url"));
        }
      }
    }
  }
  
  private static List<ChangeSetSyncDTO> getChangeSets(IFilesystemRestClient restClient, String repositoryUrl, String workspaceItemId, List<String> changeSetItemIds, IScmClientConfiguration config) throws FileSystemException
  {
    if (changeSetItemIds.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    
    ParmsGetChangeSets parmsGetChangeSets = new ParmsGetChangeSets();
    changeSetItemIds = ((String[])changeSetItemIds.toArray(new String[changeSetItemIds.size()]));
    repositoryUrl = repositoryUrl;
    
    ParmsResolveChangeSet csParms = new ParmsResolveChangeSet();
    context = new ParmsContext();
    
    context.repositoryUrl = repositoryUrl;
    context.itemId = workspaceItemId;
    context.type = "workspace";
    
    includeChanges = Boolean.valueOf(true);
    includeFoldersInChangeLists = Boolean.valueOf(true);
    settings = csParms;
    try {
      ChangeSetSyncDTO[] result = restClient.getChangeSets(parmsGetChangeSets, null);
      return Arrays.asList(result);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.AcceptResultDisplayer_FETCH_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
  }
  

  private static void jsonizeBaselines(JSONArray jBaselines, List<StructuredBaselineUpdateReportDTO> baselineDtos, String repoUrl)
  {
    for (StructuredBaselineUpdateReportDTO blDto : baselineDtos) {
      JSONObject jBaseline = new JSONObject();
      jBaseline.put("uuid", blDto.getBaselineItemId());
      jBaseline.put("id", Long.valueOf(blDto.getBaselineId()));
      jBaseline.put("name", blDto.getBaselineName());
      jBaselines.add(jBaseline);
    }
  }
  
  private static void showBaselines(JSONArray jBaselines, IndentingPrintStream out, String repoUrl)
  {
    for (Object obj : jBaselines)
    {
      JSONObject jBaseline = (JSONObject)obj;
      

      String alias = AliasUtil.alias((String)jBaseline.get("uuid"), repoUrl, RepoUtil.ItemType.BASELINE);
      

      String id = Long.toString(((Long)jBaseline.get("id")).longValue());
      

      String name = (String)jBaseline.get("name");
      
      out.println(NLS.bind(Messages.AcceptResultDisplayer_BASELINE_DESCRIPTION, new String[] { alias, id, name }));
    }
  }
  
  public static String getChangeSetFlags(boolean hasConflict, boolean hasCollision, boolean isActive) {
    StringBuilder flags = new StringBuilder();
    
    flags.append(hasCollision ? "!" : "-");
    flags.append(hasConflict ? "#" : "-");
    flags.append(isActive ? "@" : "$");
    return flags.toString();
  }
  
  public static void showOutOfSync(List<ShareDTO> outOfSyncShareList, IScmClientConfiguration config) throws FileSystemException {
    if (outOfSyncShareList.size() > 0)
    {
      Map<String, List<ShareDTO>> ws2Shares = new HashMap();
      
      for (ShareDTO share : outOfSyncShareList) {
        List<ShareDTO> shares = (List)ws2Shares.get(share.getContextItemId());
        if (shares == null) {
          shares = new ArrayList();
          ws2Shares.put(share.getContextItemId(), shares);
        }
        
        shares.add(share);
      }
      
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.AcceptCmd_OUT_OF_SYNC);
      
      Object itemSet = new HashSet();
      Iterator localIterator3; for (Iterator localIterator2 = ws2Shares.entrySet().iterator(); localIterator2.hasNext(); 
          




          localIterator3.hasNext())
      {
        Map.Entry<String, List<ShareDTO>> entry = (Map.Entry)localIterator2.next();
        err.println(NLS.bind(Messages.AcceptResultDisplayer_WORKSPACE_DESCRIPTION, 
          AliasUtil.selector(((ShareDTO)((List)entry.getValue()).get(0)).getContextName(), 
          UUID.valueOf(((ShareDTO)((List)entry.getValue()).get(0)).getContextItemId()), 
          config.getRepositoryURI(((ShareDTO)((List)entry.getValue()).get(0)).getRepositoryId(), null), 
          RepoUtil.ItemType.WORKSPACE)));
        localIterator3 = ((List)entry.getValue()).iterator(); continue;ShareDTO share = (ShareDTO)localIterator3.next();
        if (!((Set)itemSet).contains(share.getComponentItemId())) {
          err.indent().println(AliasUtil.selector(share.getComponentName(), 
            UUID.valueOf(share.getComponentItemId()), config.getRepositoryURI(share.getRepositoryId(), null), 
            RepoUtil.ItemType.COMPONENT));
          ((Set)itemSet).add(share.getComponentItemId());
        }
      }
      

      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, LoadCmdLauncher.class);
      throw StatusHelper.outOfSync(
        NLS.bind(
        Messages.CheckInCmd_9, 
        new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(defnTemp).toString(), 
        ((OptionKey)LoadCmdOptions.OPT_FORCE).getName(), 
        ((OptionKey)LoadCmdOptions.OPT_RESYNC).getName() }));
    }
  }
}
