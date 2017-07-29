package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetBaselines;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.BaselineHistoryEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.GetBaselinesDTO;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.IItem;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineHandle;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;

public class ListBaselineCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListBaselineCmd() {}
  
  public static final NamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition("w", "workspace", 1, "@");
  
  public static final NamedOptionDefinition OPT_COMPONENTS_SELECTOR = new NamedOptionDefinition("C", "components", -1, "@");
  public static final NamedOptionDefinition OPT_SNAPSHOT = new NamedOptionDefinition("s", "snapshot", 1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    options.addOption(new ContinuousGroup()
      .addOption(OPT_SNAPSHOT, Messages.ListBaselineCmd_SS_HELP, true)
      .addOption(OPT_COMPONENTS_SELECTOR, CommonOptions.OPT_COMPONENTS_SELECTOR_HELP, false))
      .addOption(new ContinuousGroup()
      .addOption(OPT_WORKSPACE, Messages.ListBaselineCmd_WS_HELP, true)
      .addOption(OPT_COMPONENTS_SELECTOR, CommonOptions.OPT_COMPONENTS_SELECTOR_HELP, false))
      .addOption(new ContinuousGroup().addOption(OPT_COMPONENTS_SELECTOR, CommonOptions.OPT_COMPONENTS_SELECTOR_HELP, true));
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_MAXRESULTS, Messages.ListBaselineCmd_OPT_MAX);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if ((!cli.hasOption(OPT_WORKSPACE)) && (!cli.hasOption(OPT_SNAPSHOT)) && (!cli.hasOption(OPT_COMPONENTS_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ListBaselineCmd_SPECIFY_ONE_OPT, new String[] {
        OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName(), OPT_COMPONENTS_SELECTOR.getName() }));
    }
    
    if ((cli.hasOption(OPT_WORKSPACE)) && (cli.hasOption(OPT_SNAPSHOT))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        OPT_WORKSPACE.getName(), OPT_SNAPSHOT.getName()));
    }
    

    int max = RepoUtil.getMaxResultsOption(cli, 10);
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    WorkspaceDetailsDTO wsDetails = null;
    IBaselineSet ss = null;
    List<BaselineDTO> baselineDTOList = null;
    
    if (cli.hasOption(OPT_WORKSPACE)) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    } else if (cli.hasOption(OPT_SNAPSHOT)) {
      IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SNAPSHOT), config);
      SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
      ss = RepoUtil.getSnapshot(null, ssSelector.getItemSelector(), repo, config);
    }
    
    List<IScmCommandLineArgument> componentSelectors = null;
    Map<SubcommandUtil.ItemInfo, ITeamRepository> compMap = new HashMap();
    Map.Entry<ITeamRepository, List<String>> entry;
    if (cli.hasOption(OPT_COMPONENTS_SELECTOR)) {
      componentSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_COMPONENTS_SELECTOR), config);
      SubcommandUtil.validateArgument(componentSelectors, RepoUtil.ItemType.COMPONENT);
      

      Map<ITeamRepository, List<String>> repoToCompSelectors = new HashMap();
      boolean compFound;
      SubcommandUtil.ItemInfo itemInfo;
      if (ws != null) {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, componentSelectors, repo, config);
        

        for (IScmCommandLineArgument compSelector : componentSelectors) {
          compFound = false;
          IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector.getItemSelector());
          
          for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
            if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(compDTO.getItemId()))) || 
              (compSelector.getItemSelector().equals(compDTO.getName()))) {
              itemInfo = new SubcommandUtil.ItemInfo(compDTO.getName(), compDTO.getItemId(), repositoryUrl, 
                RepoUtil.ItemType.COMPONENT);
              compMap.put(itemInfo, repo);
              compFound = true;
              break;
            }
          }
          if (!compFound)
            throw StatusHelper.argSyntax(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
        } } else { Object compSelector;
        IUuidAliasRegistry.IUuidAlias compAlias;
        if (ss != null) {
          RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, componentSelectors, repo, config);
          
          baselineDTOList = getBaselinesInSnapshot(ss, repo, client);
          List<IComponent> comps = getComponentsInSnapshot(baselineDTOList, repo, client);
          

          for (compFound = componentSelectors.iterator(); compFound.hasNext();) { compSelector = (IScmCommandLineArgument)compFound.next();
            boolean compFound = false;
            compAlias = RepoUtil.lookupUuidAndAlias(((IScmCommandLineArgument)compSelector).getItemSelector());
            
            for (IComponent comp : comps) {
              if (((compAlias != null) && (compAlias.getUuid().equals(comp.getItemId()))) || 
                (((IScmCommandLineArgument)compSelector).getItemSelector().equals(comp.getName()))) {
                SubcommandUtil.ItemInfo itemInfo = new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId().getUuidValue(), 
                  repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT);
                compMap.put(itemInfo, repo);
                compFound = true;
                break;
              }
            }
            
            if (!compFound) {
              throw StatusHelper.argSyntax(NLS.bind(Messages.Common_COMP_NOT_FOUND, ((IScmCommandLineArgument)compSelector).getItemSelector()));
            }
          }
        } else {
          for (compSelector = componentSelectors.iterator(); ((Iterator)compSelector).hasNext();) { IScmCommandLineArgument compSelector = (IScmCommandLineArgument)((Iterator)compSelector).next();
            
            ITeamRepository compRepo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
            List<String> compSelectors = (List)repoToCompSelectors.get(compRepo);
            if (compSelectors == null) {
              compSelectors = new ArrayList();
              repoToCompSelectors.put(compRepo, compSelectors);
            }
            compSelectors.add(compSelector.getItemSelector());
          }
          

          for (compSelector = repoToCompSelectors.entrySet().iterator(); ((Iterator)compSelector).hasNext(); 
              
              compAlias.hasNext())
          {
            entry = (Map.Entry)((Iterator)compSelector).next();
            List<IComponent> comps = RepoUtil.getComponents((List)entry.getValue(), (ITeamRepository)entry.getKey(), config);
            compAlias = comps.iterator(); continue;IComponent comp = (IComponent)compAlias.next();
            SubcommandUtil.ItemInfo itemInfo = new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId().getUuidValue(), 
              ((ITeamRepository)entry.getKey()).getRepositoryURI(), RepoUtil.ItemType.COMPONENT);
            compMap.put(itemInfo, (ITeamRepository)entry.getKey());
          }
        }
      } } else { Object itemInfo;
      if (ws != null) {
        for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
          itemInfo = new SubcommandUtil.ItemInfo(compDTO.getName(), compDTO.getItemId(), repositoryUrl, 
            RepoUtil.ItemType.COMPONENT);
          compMap.put(itemInfo, repo);
        }
      } else {
        baselineDTOList = getBaselinesInSnapshot(ss, repo, client);
        List<IComponent> comps = getComponentsInSnapshot(baselineDTOList, repo, client);
        for (itemInfo = comps.iterator(); ((Iterator)itemInfo).hasNext();) { IComponent comp = (IComponent)((Iterator)itemInfo).next();
          SubcommandUtil.ItemInfo itemInfo = new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId().getUuidValue(), 
            repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT);
          compMap.put(itemInfo, repo);
        }
      }
    }
    
    JSONArray comps = new JSONArray();
    if (ss != null) {
      jsonizeBaselinesInSnapshot(baselineDTOList, max, compMap, client, repo, comps, new HashMap());
    } else {
      jsonizeBaselines(ws, max, compMap, client, comps, new HashMap());
    }
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(comps);
    } else {
      printBaselines(comps, new IndentingPrintStream(config.getContext().stdout()));
    }
  }
  
  private List<BaselineDTO> getBaselinesInSnapshot(IBaselineSet ss, ITeamRepository repo, IFilesystemRestClient client) throws FileSystemException
  {
    List<String> baselineIds = new ArrayList();
    for (IBaselineHandle blHandle : ss.getBaselines()) {
      baselineIds.add(blHandle.getItemId().getUuidValue());
    }
    

    return RepoUtil.getBaselinesById(baselineIds, repo.getRepositoryURI(), client, config);
  }
  
  private List<IComponent> getComponentsInSnapshot(List<BaselineDTO> baselineDTOList, ITeamRepository repo, IFilesystemRestClient client)
    throws FileSystemException
  {
    List<String> compIds = new ArrayList();
    for (BaselineDTO blDTO : baselineDTOList) {
      compIds.add(blDTO.getComponentItemId());
    }
    
    return RepoUtil.getComponents(compIds, repo, config);
  }
  
  private void printBaselines(JSONArray comps, IndentingPrintStream printStream)
  {
    if (comps.size() == 0) {
      return;
    }
    
    for (Object obj : comps) {
      JSONObject comp = (JSONObject)obj;
      
      printStream.println(NLS.bind(Messages.ListComponentsCmd_Component, 
        AliasUtil.selector((String)comp.get("name"), UUID.valueOf((String)comp.get("uuid")), 
        (String)comp.get("url"), RepoUtil.ItemType.COMPONENT)));
      
      JSONArray baselines = (JSONArray)comp.get("baselines");
      
      if (baselines != null)
      {
        IndentingPrintStream printStr = printStream.indent();
        for (Object blObj : baselines) {
          JSONObject baseline = (JSONObject)blObj;
          
          printStr.printIndent();
          if (baseline.get("id") == null) {
            String msg = NLS.bind(Messages.ListBaselineCmd_CREATED_BY, baseline.get("contributor"), baseline.get("creationDate"));
            printStr.print(NLS.bind(Messages.ListBaselineCmd_BASELINE, "  ", "  \"\"  " + msg));
          } else {
            String msg = NLS.bind(Messages.ListBaselineCmd_CREATED_BY, baseline.get("contributor"), baseline.get("creationDate"));
            printStr.print(NLS.bind(Messages.ListBaselineCmd_BASELINE, 
              AliasUtil.selector(((Integer)baseline.get("id")).intValue(), (String)baseline.get("name"), UUID.valueOf((String)baseline.get("uuid")), 
              (String)comp.get("url"), RepoUtil.ItemType.BASELINE), "  " + baseline.get("comment") + "  " + msg));
          }
          String deliveredBy = (String)baseline.get("added-by");
          if (deliveredBy != null) {
            String deliveryDate = (String)baseline.get("date-added");
            printStr.print("  " + NLS.bind(Messages.ListBaselineCmd_ADDED_BY, deliveredBy, deliveryDate));
          }
          printStr.println();
        }
      }
    }
  }
  

  List<SubcommandUtil.ItemInfo> sort(List<SubcommandUtil.ItemInfo> list)
  {
    Collections.sort(list, new Comparator() {
      public int compare(SubcommandUtil.ItemInfo comp1, SubcommandUtil.ItemInfo comp2) {
        return comp1.getName().compareTo(comp2.getName());
      }
      
    });
    return list;
  }
  
  private void jsonizeBaselineHistory(ITeamRepository repo, IClientConfiguration config, BaselineHistoryEntryDTO blHistoryDTO, DateFormat dt, JSONObject bl, Map<String, String> userCache) throws FileSystemException
  {
    bl.put("creationDate", dt.format(new Date(blHistoryDTO.getDeliveryDate())));
    

    String selector = blHistoryDTO.getDeliveredByItemId();
    bl.put("contributor", fetchContributor(repo, selector, userCache));
  }
  
  private String fetchContributor(ITeamRepository repo, String itemId, Map<String, String> userCache)
    throws FileSystemException
  {
    String name = (String)userCache.get(itemId);
    if (name != null) {
      return name;
    }
    
    UUID uuid = UUID.valueOf(itemId);
    IContributorHandle contribHandle = (IContributorHandle)IContributor.ITEM_TYPE.createItemHandle(uuid, null);
    try {
      IItem contributor = repo.itemManager().fetchCompleteItem(contribHandle, 0, null);
      
      if (IContributor.ITEM_TYPE.equals(contributor.getItemType())) {
        name = ((IContributor)contributor).getName();
        userCache.put(itemId, name);
        return name;
      }
      
      return itemId;
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ListUsersCmd_UNABLE_TO_LIST_USERS, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  
  private void jsonizeBaseline(ITeamRepository repo, BaselineDTO baseline, IndentingPrintStream printStream, DateFormat dt, JSONObject bl, Map<String, String> userCache) {
    jsonizeBaseline(repo, baseline, null, printStream, dt, bl, userCache);
  }
  
  private void jsonizeBaseline(ITeamRepository repo, BaselineDTO baseline, BaselineHistoryEntryDTO history, IndentingPrintStream printStream, DateFormat dt, JSONObject bl, Map<String, String> userCache)
  {
    printStream = printStream.indent();
    
    bl.put("id", Integer.valueOf(baseline.getId()));
    bl.put("name", baseline.getName());
    bl.put("uuid", baseline.getItemId());
    bl.put("creationDate", dt.format(Long.valueOf(baseline.getCreationDate())));
    bl.put("contributor", baseline.getCreatorContributorName());
    if (history != null) {
      String deliveredBy = baseline.getCreatorContributorName();
      try {
        String itemId = history.getDeliveredByItemId();
        if (!itemId.equals(baseline.getCreatorContributorItemId())) {
          deliveredBy = fetchContributor(repo, history.getDeliveredByItemId(), userCache);
        }
        bl.put("added-by", deliveredBy);
        bl.put("date-added", dt.format(Long.valueOf(history.getDeliveryDate())));
      }
      catch (FileSystemException e) {
        String msg = NLS.bind(Messages.ErrorFetchingUser, e.getMessage());
        StatusHelper.logException(msg, e);
      }
    }
    
    String blComment = "\"" + baseline.getComment() + "\"";
    
    bl.put("comment", blComment);
  }
  
  private void jsonizeBaselines(ParmsWorkspace ws, int max, Map<SubcommandUtil.ItemInfo, ITeamRepository> compMap, IFilesystemRestClient client, JSONArray comps, Map<String, String> userCache) throws FileSystemException
  {
    List<SubcommandUtil.ItemInfo> compList = sort(new ArrayList(compMap.keySet()));
    

    ParmsGetBaselines parms = new ParmsGetBaselines();
    if (ws != null) {
      workspaceItemId = workspaceItemId;
      repositoryUrl = repositoryUrl;
      max = Integer.valueOf(max);
    }
    
    DateFormat dt = SubcommandUtil.getDateFormat(null, config);
    
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    

    for (SubcommandUtil.ItemInfo comp : compList) {
      componentItemId = comp.getId();
      ITeamRepository repo = (ITeamRepository)compMap.get(comp);
      repositoryUrl = repo.getRepositoryURI();
      
      GetBaselinesDTO result = null;
      try {
        result = client.getBaselines(parms, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(
          NLS.bind(Messages.ListBaselineCmd_GET_BASELINES_FAILURE, AliasUtil.selector(comp.getName(), 
          UUID.valueOf(comp.getId()), comp.getRepoUri(), RepoUtil.ItemType.COMPONENT)), 
          e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
      
      JSONObject jComp = JSONPrintUtil.jsonize(comp.getName(), comp.getId(), repo.getRepositoryURI());
      
      comps.add(jComp);
      
      JSONArray baselines = new JSONArray();
      
      jComp.put("baselines", baselines);
      
      if (ws != null) {
        List<BaselineHistoryEntryDTO> baselineEntries = result.getBaselineHistoryEntriesInWorkspace();
        

        for (int idx = baselineEntries.size() - 1; idx >= 0; idx--) {
          BaselineHistoryEntryDTO blHistoryDTO = (BaselineHistoryEntryDTO)baselineEntries.get(idx);
          BaselineDTO baseline = blHistoryDTO.getBaseline();
          
          JSONObject bl = new JSONObject();
          if (baseline == null) {
            jsonizeBaselineHistory(repo, config, blHistoryDTO, dt, bl, userCache);
          } else {
            jsonizeBaseline(repo, baseline, blHistoryDTO, printStream, dt, bl, userCache);
          }
          
          baselines.add(bl);
        }
      } else {
        List<BaselineDTO> blDTO = result.getBaselinesInRepository();
        

        int cnt = 0; for (int idx = blDTO.size() - 1; (cnt < max) && (idx >= 0); cnt++) {
          JSONObject bl = new JSONObject();
          BaselineDTO baseline = (BaselineDTO)blDTO.get(idx);
          
          jsonizeBaseline(repo, baseline, printStream, dt, bl, userCache);
          
          baselines.add(bl);idx--;
        }
      }
    }
  }
  





  private void jsonizeBaselinesInSnapshot(List<BaselineDTO> baselineDTOList, int max, Map<SubcommandUtil.ItemInfo, ITeamRepository> compMap, IFilesystemRestClient client, ITeamRepository repo, JSONArray comps, Map<String, String> userCache)
    throws FileSystemException
  {
    List<SubcommandUtil.ItemInfo> compList = sort(new ArrayList(compMap.keySet()));
    
    DateFormat dt = DateFormat.getDateTimeInstance();
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    Iterator localIterator2;
    for (Iterator localIterator1 = compList.iterator(); localIterator1.hasNext(); 
        





        localIterator2.hasNext())
    {
      SubcommandUtil.ItemInfo comp = (SubcommandUtil.ItemInfo)localIterator1.next();
      JSONObject jComp = JSONPrintUtil.jsonize(comp.getName(), comp.getId(), repo.getRepositoryURI());
      comps.add(jComp);
      
      JSONArray baselines = new JSONArray();
      jComp.put("baselines", baselines);
      
      localIterator2 = baselineDTOList.iterator(); continue;BaselineDTO blDTO = (BaselineDTO)localIterator2.next();
      if (blDTO.getComponentItemId().equals(comp.getId())) {
        JSONObject bl = new JSONObject();
        jsonizeBaseline(repo, blDTO, printStream, dt, bl, userCache);
        
        baselines.add(bl);
      }
    }
  }
}
