package com.ibm.team.filesystem.cli.client.internal.flowcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.lock.LockListCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ConflictsCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.StatusCmd;
import com.ibm.team.filesystem.cli.client.internal.workspace.AddComponentsCmd;
import com.ibm.team.filesystem.cli.client.util.ChangeSetUtil;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.ComponentSyncDTOComparator;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangeLogCE;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCompareCE;
import com.ibm.team.filesystem.client.rest.parameters.ParmsContext;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliver;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliverChangeSetsOrBaselines;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliverComponents;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliverDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliverWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeliveryRequiresHistoryReorderingDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsStructuredResultOptions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.DeliverResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.DeliveryRequiresHistoryReorderingDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.BaselineSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkItemSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyNodeDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.StaleDataException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.subcommands.HelpCmd;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.ChangeSetAlreadyInHistoryException;
import com.ibm.team.scm.common.ChangeSetsNotOutgoingException;
import com.ibm.team.scm.common.DeliveryIntroducesConflictsException;
import com.ibm.team.scm.common.GapException;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.OutstandingConflictsException;
import com.ibm.team.scm.common.StreamLockedException;
import com.ibm.team.scm.common.SyncReportInappropriateException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;


















public class DeliverCmd
  extends AbstractSubcommand
{
  IFilesystemRestClient client = null;
  ParmsWorkspace sourceWs = null;
  WorkspaceDetailsDTO srcWsDetails = null;
  List<ChangeSetSyncDTO> unresolvedCsList = null;
  
  static enum Mode { UNSET,  WS,  CS,  COMPONENT,  BASELINE,  WI;
  }
  
  public DeliverCmd() {}
  
  private static List<String> getOutgoingChanges(ParmsWorkspace srcWs, ParmsWorkspace tgtWs, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException { List<String> outgoingChanges = new ArrayList();
    
    String compareId = compare(srcWs, tgtWs, client, config);
    
    ParmsChangeLogCE parmsChangeLog = new ParmsChangeLogCE();
    inputID = compareId;
    includeChangeSets = true;
    includeBaselines = false;
    
    ChangeLogEntryDTO changeLogEntry = getOutgoing(parmsChangeLog, repositoryUrl, client, config);
    
    if (!changeLogEntry.getEntryType().equals("clentry_changeset")) {
      for (ChangeLogEntryDTO entryDTO : changeLogEntry.getChildEntries()) {
        if (entryDTO.getEntryType().equals("clentry_changeset")) {
          outgoingChanges.add(entryDTO.getItemId());
        }
      }
    }
    
    return outgoingChanges;
  }
  
  private static Map<String, String> getOutgoingBaselines(ParmsWorkspace srcWs, ParmsWorkspace tgtWs, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<String, String> outgoingBaselines = new HashMap();
    
    String compareId = compare(srcWs, tgtWs, client, config);
    
    ParmsChangeLogCE parmsChangeLog = new ParmsChangeLogCE();
    inputID = compareId;
    includeBaselines = true;
    includeChangeSets = false;
    
    ChangeLogEntryDTO changeLogEntry = getOutgoing(parmsChangeLog, repositoryUrl, client, config);
    
    if (!changeLogEntry.getEntryType().equals("clentry_baseline")) {
      for (ChangeLogEntryDTO entryDTO : changeLogEntry.getChildEntries()) {
        if (entryDTO.getEntryType().equals("clentry_baseline")) {
          outgoingBaselines.put(entryDTO.getItemId(), entryDTO.getEntryName());
        }
      }
    }
    
    return outgoingBaselines;
  }
  
  private static String compare(ParmsWorkspace srcWs, ParmsWorkspace tgtWs, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    ParmsCompareCE parmsCompare = new ParmsCompareCE();
    context1 = new ParmsContext("workspace", repositoryUrl, workspaceItemId);
    context2 = new ParmsContext("workspace", repositoryUrl, workspaceItemId);
    
    String compareId = null;
    try {
      compareId = client.postCompareCE(parmsCompare, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.DeliverCmd_ERROR_ON_WS_COMPARE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    return compareId;
  }
  
  private static ChangeLogEntryDTO getOutgoing(ParmsChangeLogCE parmsChangeLog, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    flowDirection = "outgoing";
    includeComponents = false;
    includeContributors = false;
    includeDirection = false;
    includePaths = false;
    includeWorkItems = false;
    pruneEmptyDirections = true;
    pruneUnchangedComponents = true;
    
    ChangeLogEntryDTO changeLogEntry = null;
    try {
      changeLogEntry = client.getChangeLogCE(parmsChangeLog, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.DeliverCmd_ERROR_ON_GET_CHANGELOG, e, 
        new IndentingPrintStream(config.getContext().stderr()), repoUri);
    }
    
    return changeLogEntry;
  }
  
  private static boolean isCsSelectorMatchesWorkItem(ChangeSetSyncDTO cs, String selector)
  {
    boolean toReturn = false;
    
    for (WorkItemSyncDTO wi : cs.getWorkItems())
    {

      String[] tokens = wi.getLabel().split(":");
      
      if (tokens[0].equals(selector)) {
        toReturn = true;
        break;
      }
    }
    
    return toReturn;
  }
  
  private static List<ChangeSetSyncDTO> getMatchedCsList(List<ChangeSetSyncDTO> csList, List<String> csSelectors, Set<String> matchedSelectors, IScmClientConfiguration config) throws FileSystemException
  {
    List<ChangeSetSyncDTO> normalized = new ArrayList();
    
    if ((csSelectors != null) && (csSelectors.size() > 0)) {
      Set<ChangeSetSyncDTO> tempCsList = new HashSet();
      if ((csList != null) && (csList.size() > 0)) {
        for (String selector : csSelectors) {
          for (ChangeSetSyncDTO cs : csList)
          {

            if ((cs.getChangeSetComment().equals(selector)) || 
              (RepoUtil.uuidAndRepoMatches(UUID.valueOf(cs.getChangeSetItemId()), cs.getRepositoryUrl(), RepoUtil.lookupUuidAndAlias(selector))) || 
              (isCsSelectorMatchesWorkItem(cs, selector))) {
              tempCsList.add(cs);
              
              matchedSelectors.add(selector);
              break;
            }
          }
        }
      }
      
      normalized.addAll(tempCsList);
    }
    
    return normalized;
  }
  
  private static List<BaselineSyncDTO> getMatchedBlList(List<BaselineSyncDTO> blList, List<String> blSelectors, Set<String> matchedSelectors, IScmClientConfiguration config, String repoUrl) throws FileSystemException
  {
    List<BaselineSyncDTO> normalized = new ArrayList();
    
    if ((blSelectors != null) && (blSelectors.size() > 0)) {
      Set<BaselineSyncDTO> tempBlList = new HashSet();
      if ((blList != null) && (blList.size() > 0)) {
        for (String selector : blSelectors) {
          for (BaselineSyncDTO bl : blList)
          {
            if (RepoUtil.uuidAndRepoMatches(UUID.valueOf(bl.getBaselineItemId()), repoUrl, RepoUtil.lookupUuidAndAlias(selector)))
            {
              tempBlList.add(bl);
              
              matchedSelectors.add(selector);
              break;
            }
          }
        }
      }
      
      normalized.addAll(tempBlList);
    }
    
    return normalized;
  }
  
  private static ChangeSetSyncDTO[] normalizeCsUuidAndAlias(ParmsWorkspace srcWs, ParmsWorkspace tgtWs, List<String> csSelectors, IScmClientConfiguration config, IFilesystemRestClient client) throws FileSystemException
  {
    Map<String, String> csItemIdToSelectorMap = new HashMap();
    Set<String> unmatchedSelectors = new HashSet();
    

    List<String> csList = getOutgoingChanges(srcWs, tgtWs, client, config);
    
    for (String selector : csSelectors) {
      boolean matched = false;
      IUuidAliasRegistry.IUuidAlias uuid = RepoUtil.lookupUuidAndAlias(selector);
      
      if (uuid != null) {
        for (String csId : csList) {
          if (RepoUtil.uuidAndRepoMatches(UUID.valueOf(csId), repositoryUrl, uuid)) {
            csItemIdToSelectorMap.put(uuid.getUuid().getUuidValue(), selector);
            matched = true;
            break;
          }
        }
      }
      
      if (!matched) {
        unmatchedSelectors.add(selector);
      }
    }
    
    if (unmatchedSelectors.size() > 0) {
      printUnresolvedChangeSetSelectors(unmatchedSelectors, config);
    }
    
    ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(new ArrayList(csItemIdToSelectorMap.keySet()), 
      false, workspaceItemId, "workspace", repositoryUrl, client, config);
    
    return csDTOList;
  }
  
  private static List<BaselineDTO> normalizeBlUuidAndAlias(ParmsWorkspace sourceWs, ParmsWorkspace tgtWs, List<String> blSelectors, IScmClientConfiguration config, IFilesystemRestClient client) throws FileSystemException
  {
    Map<String, String> blItemIdToSelectorMap = new HashMap();
    Set<String> unmatchedSelectors = new HashSet();
    

    Map<String, String> blList = getOutgoingBaselines(sourceWs, tgtWs, client, config);
    
    for (String selector : blSelectors) {
      boolean matched = false;
      IUuidAliasRegistry.IUuidAlias uuid = RepoUtil.lookupUuidAndAlias(selector);
      if (uuid != null) {
        for (String blId : blList.keySet()) {
          if (RepoUtil.uuidAndRepoMatches(UUID.valueOf(blId), repositoryUrl, uuid)) {
            blItemIdToSelectorMap.put(uuid.getUuid().getUuidValue(), selector);
            matched = true;
            break;
          }
        }
      } else if (blList.values().contains(selector)) {
        blItemIdToSelectorMap.put(uuid.getUuid().getUuidValue(), selector);
        matched = true;
      }
      
      if (!matched) {
        unmatchedSelectors.add(selector);
      }
    }
    
    if (unmatchedSelectors.size() > 0) {
      printUnresolvedBaselines(unmatchedSelectors, config);
    }
    
    List<BaselineDTO> baselines = RepoUtil.getBaselinesById(new ArrayList(blItemIdToSelectorMap.keySet()), 
      repositoryUrl, client, config);
    
    return baselines;
  }
  
  private static String matchesSelector(IScmClientConfiguration config, String componentItemId, String componentName, Set<String> selectors, String repoUri) throws FileSystemException
  {
    String compAlias = null;
    
    IUuidAliasRegistry.IUuidAlias alias = RepoUtil.lookupUuidAndAlias(componentItemId, repoUri);
    if (alias != null) {
      compAlias = alias.getMonicker();
    }
    
    for (String selector : selectors) {
      if ((componentItemId.equals(selector)) || 
        (selector.equals(compAlias)) || 
        (selector.equals(componentName))) {
        return selector;
      }
    }
    
    return null;
  }
  
  private static List<ChangeSetSyncDTO> getCsListFromCsCompMapList(Map<ComponentSyncDTO, List<ChangeSetSyncDTO>> compCsMapList)
  {
    List<ChangeSetSyncDTO> csList = new LinkedList();
    
    for (Map.Entry<ComponentSyncDTO, List<ChangeSetSyncDTO>> compCsMapSet : compCsMapList.entrySet()) {
      csList.addAll((Collection)compCsMapSet.getValue());
    }
    
    return csList;
  }
  

  private static List<BaselineSyncDTO> getBlListFromBlCompMapList(Map<ComponentSyncDTO, List<BaselineSyncDTO>> compBlMapList)
  {
    List<BaselineSyncDTO> blList = new LinkedList();
    
    for (Map.Entry<ComponentSyncDTO, List<BaselineSyncDTO>> compCsMapSet : compBlMapList.entrySet()) {
      blList.addAll((Collection)compCsMapSet.getValue());
    }
    
    return blList;
  }
  

  private Map<ParmsWorkspace, Set<ComponentSyncDTO>> getTargetWsCompAndChangeSetSyncMapWithCompSelectors(ParmsWorkspace source, List<String> compSelectorList)
    throws FileSystemException
  {
    Map<ParmsWorkspace, Set<ComponentSyncDTO>> componentFlows = new HashMap();
    
    Set<String> compSelectorSet = null;
    if (compSelectorList != null) {
      compSelectorSet = new HashSet(compSelectorList);
    }
    
    Set<String> matchedSelectorSet = new HashSet();
    

    List<ParmsWorkspace> wsList = new ArrayList();
    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(source), false, client, config);
    for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces())
    {
      if (wsSync.getWorkspaceItemId().equals(workspaceItemId))
      {

        for (ComponentSyncDTO compSync : wsSync.getComponents()) {
          String matchedSelector = null;
          
          if ((compSelectorSet != null) && (compSelectorSet.size() > 0)) {
            matchedSelector = matchesSelector(config, compSync.getComponentItemId(), 
              compSync.getComponentName(), compSelectorSet, wsSync.getRepositoryUrl());
            
            if (matchedSelector != null) {
              matchedSelectorSet.add(matchedSelector);


            }
            


          }
          else if ((compSync.getTargetOutgoingWorkspaceItemId() == null) || ((compSync.getTargetOutgoingWorkspaceItemId().equals(wsSync.getWorkspaceItemId())) && 
            (compSync.getTargetOutgoingRepositoryUrl().equals(wsSync.getRepositoryUrl())))) {
            if (matchedSelector != null)
            {
              Set<ComponentSyncDTO> allComponents = new HashSet(wsSync.getComponents());
              printNonCollaboratedComponents(wsSync.getWorkspaceName(), wsSync.getWorkspaceItemId(), 
                wsSync.getRepositoryUrl(), allComponents, config);
            }
            

          }
          else
          {

            ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetOutgoingRepositoryUrl(), compSync.getTargetOutgoingWorkspaceItemId());
            ParmsWorkspace wsMatched = findWorkspace(wsList, ws);
            
            Set<ComponentSyncDTO> components = null;
            if (wsMatched != null) {
              components = (Set)componentFlows.get(wsMatched);
            }
            
            if (components == null) {
              components = new HashSet();
              
              componentFlows.put(ws, components);
              wsList.add(ws);
            }
            
            components.add(compSync);
            
            if ((compSelectorSet != null) && (compSelectorSet.size() == 0)) {
              break;
            }
          }
        }
        
        if ((compSelectorSet != null) || (componentFlows.size() != 0)) break;
        throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_NO_DELIVERABLE_COMPONENT_FOUND);
      }
    }
    



    if (compSelectorSet != null)
    {
      compSelectorSet.removeAll(matchedSelectorSet);
      

      if (compSelectorSet.size() > 0) {
        printUnresolvedComponentSelector(null, compSelectorSet, config);
      }
    }
    
    return componentFlows;
  }
  
  ParmsWorkspace findWorkspace(List<ParmsWorkspace> wsList, ParmsWorkspace ws) {
    for (ParmsWorkspace wsInList : wsList) {
      if ((repositoryUrl.equals(repositoryUrl)) && (workspaceItemId.equals(workspaceItemId))) {
        return wsInList;
      }
    }
    
    return null;
  }
  
  private static void printNonCollaboratedComponents(String wsName, String sourceWsItemId, String repoUrl, Set<ComponentSyncDTO> allComponents, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    
    err.println(Messages.FlowCmd_NO_DEFAULT_COLLABORATION);
    err = err.indent();
    
    for (ComponentSyncDTO component : allComponents) {
      if (component.getTargetOutgoingWorkspaceItemId().equals(sourceWsItemId)) {
        err.println(AliasUtil.selector(component.getComponentName(), UUID.valueOf(component.getComponentItemId()), 
          repoUrl, RepoUtil.ItemType.COMPONENT));
      }
    }
    
    throw StatusHelper.misconfiguredLocalFS(NLS.bind(Messages.FlowCmd_NO_DEFAULT_COLLABORATION_ERROR, wsName));
  }
  
  private static void printUnresolvedComponentSelector(String wsInfo, Set<String> compSelectorSet, IScmClientConfiguration config) throws FileSystemException
  {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    if (wsInfo != null) {
      err.println(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_NOT_IN_WORKSPACE, wsInfo));
    } else {
      err.println(Messages.DeliverCmd_UNRESOLVED_COMPONENT_SELECTOR_LIST);
    }
    err.indent();
    for (String componentSelector : compSelectorSet) {
      err.println(componentSelector);
    }
    throw StatusHelper.ambiguousSelector(Messages.DeliverCmd_UNRESOLVED_COMPONENT_SELECTOR_MESSAGE_FOR_WS);
  }
  
  static void printUnresolvedChangeSetSelectors(Set<String> csSelectorSet, IScmClientConfiguration config) throws FileSystemException {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    err.println(Messages.DeliverCmd_UNRESOLVED_CHANGESET_SELECTOR_LIST);
    err = err.indent();
    for (String componentSelector : csSelectorSet) {
      err.println(componentSelector);
    }
    throw StatusHelper.ambiguousSelector(Messages.DeliverCmd_UNRESOLVED_CHANGESET);
  }
  
  static void printUnresolvedBaselines(Set<String> blSelectorSet, IScmClientConfiguration config) throws FileSystemException {
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    err.println(Messages.DeliverCmd_UNRESOLVED_BASELINES_LIST);
    err = err.indent();
    for (String blSelector : blSelectorSet) {
      err.println(blSelector);
    }
    throw StatusHelper.ambiguousSelector(Messages.DeliverCmd_UNRESOLVED_BASELINES);
  }
  
  private List<WorkspaceComponentDTO> getSyncCompList(ParmsWorkspace targetWs, Set<String> compSelectorList) throws FileSystemException
  {
    if ((compSelectorList == null) || (compSelectorList.isEmpty())) {
      return null;
    }
    
    List<WorkspaceComponentDTO> srcCompDTOList = srcWsDetails.getComponents();
    
    WorkspaceDetailsDTO tgtWsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
    List<WorkspaceComponentDTO> tgtCompDTOList = tgtWsDetails.getComponents();
    
    Set<String> selectorSet = new HashSet(compSelectorList);
    

    Map<String, String> componentId2Selector = new HashMap();
    Map<String, WorkspaceComponentDTO> selector2components = new HashMap();
    

    for (WorkspaceComponentDTO srcCompDTO : srcCompDTOList) {
      String matchedSelector = matchesSelector(config, srcCompDTO.getItemId(), srcCompDTO.getName(), selectorSet, 
        sourceWs.repositoryUrl);
      
      if (matchedSelector != null) {
        selectorSet.remove(matchedSelector);
        
        componentId2Selector.put(srcCompDTO.getItemId(), matchedSelector);
        selector2components.put(matchedSelector, srcCompDTO);
      }
    }
    
    if (selectorSet.size() > 0) {
      RepoUtil.ItemType itemType = srcWsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE;
      wsInfo = AliasUtil.selector(srcWsDetails.getName(), UUID.valueOf(sourceWs.workspaceItemId), 
        sourceWs.repositoryUrl, itemType);
      printUnresolvedComponentSelector((String)wsInfo, selectorSet, config);
    }
    

    selectorSet.clear();
    for (Object wsInfo = componentId2Selector.keySet().iterator(); ((Iterator)wsInfo).hasNext();) { String componentId = (String)((Iterator)wsInfo).next();
      boolean found = false;
      for (WorkspaceComponentDTO tgtCompDTO : tgtCompDTOList) {
        if (tgtCompDTO.getItemId().equals(componentId)) {
          found = true;
          break;
        }
      }
      
      if (!found) {
        String selector = (String)componentId2Selector.get(componentId);
        WorkspaceComponentDTO comp = (WorkspaceComponentDTO)selector2components.get(selector);
        selectorSet.add(AliasUtil.selector(comp.getName(), UUID.valueOf(comp.getItemId()), repositoryUrl, 
          RepoUtil.ItemType.COMPONENT));
      }
    }
    
    if (selectorSet.size() > 0) {
      RepoUtil.ItemType itemType = tgtWsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE;
      String wsInfo = AliasUtil.selector(tgtWsDetails.getName(), UUID.valueOf(workspaceItemId), 
        repositoryUrl, itemType);
      printUnresolvedComponentSelector(wsInfo, selectorSet, config);
    }
    
    return new ArrayList(selector2components.values());
  }
  

  private Map<ParmsWorkspace, Map<ComponentSyncDTO, List<ChangeSetSyncDTO>>> getTargetWsCompAndChangeSetSyncMapWithCsSelectors(List<String> csSelectorList)
    throws FileSystemException
  {
    Map<ParmsWorkspace, Map<ComponentSyncDTO, List<ChangeSetSyncDTO>>> componentFlows = 
      new HashMap();
    
    Set<String> getMatchedSelectors = new HashSet();
    List<ParmsWorkspace> wsList = new ArrayList();
    boolean isCompInFlowTgt = true;
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(sourceWs), false, client, config);
    List<String> localAddedCompList = new ArrayList();
    ParmsWorkspace wsMatched;
    for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces()) {
      if (wsSync.getWorkspaceItemId().equals(sourceWs.workspaceItemId))
      {

        for (ComponentSyncDTO compSync : wsSync.getComponents())
        {
          ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetOutgoingRepositoryUrl(), compSync.getTargetOutgoingWorkspaceItemId());
          wsMatched = findWorkspace(wsList, ws);
          
          if (compSync.isLocalAddedType()) {
            localAddedCompList.add(compSync.getComponentItemId());
          }
          
          Map<ComponentSyncDTO, List<ChangeSetSyncDTO>> compCsMap = null;
          if (wsMatched != null) {
            compCsMap = (Map)componentFlows.get(wsMatched);
          }
          
          if (compCsMap == null) {
            compCsMap = new HashMap();
            componentFlows.put(ws, compCsMap);
            wsList.add(ws);
          }
          
          List<ChangeSetSyncDTO> outgoingChangeSets = compSync.getOutgoingChangeSetsAfterBasis();
          
          if ((outgoingChangeSets != null) && (outgoingChangeSets.size() > 0)) {
            List<ChangeSetSyncDTO> normalized = getMatchedCsList(outgoingChangeSets, csSelectorList, getMatchedSelectors, config);
            if (normalized.size() > 0) {
              compCsMap.put(compSync, normalized);
            }
          }
        }
        

        break;
      }
    }
    
    csSelectorList.removeAll(getMatchedSelectors);
    
    if (csSelectorList.size() > 0) {
      Set<String> csSelectorSet = new HashSet(csSelectorList);
      Object compAliasName = new HashSet();
      
      ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(csSelectorList, 
        false, srcWsDetails.getItemId(), "workspace", srcWsDetails.getRepositoryURL(), client, config);
      

      for (int i = 0; i < csDTOList.length; i++) {
        if (localAddedCompList.contains(csDTOList[i].getComponentItemId())) {
          isCompInFlowTgt = false;
          String compAlias = AliasUtil.selector(csDTOList[i].getComponentName(), UUID.valueOf(csDTOList[i].getComponentItemId()), srcWsDetails.getRepositoryURL(), RepoUtil.ItemType.COMPONENT);
          ((Set)compAliasName).add(compAlias);
        }
      }
      
      if (!isCompInFlowTgt) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        

        err.println(Messages.DeliverCmd_COMPONENT_NOT_IN_FLOWTARGET);
        for (String compAlias : (Set)compAliasName) {
          err.indent().println(compAlias);
        }
        
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, HelpCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, AddComponentsCmd.class);
        
        err.println(NLS.bind(Messages.DeliverCmd_ADD_COMPONENT_TO_FLOWTARGET, new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString() }));
        

        StatusHelper.nothingToDeliver();
      }
      
      config.getContext().stdout().println(Messages.DeliverCmd_CHANGESET_NOT_IN_OUTGOINGLIST);
      printUnresolvedChangeSetSelectors(csSelectorSet, config);
    }
    
    return componentFlows;
  }
  
  private Map<ParmsWorkspace, Map<ComponentSyncDTO, List<BaselineSyncDTO>>> getTargetWsCompAndBaselineSyncMapWithBlSelectors(List<String> blSelectorList) throws FileSystemException
  {
    Map<ParmsWorkspace, Map<ComponentSyncDTO, List<BaselineSyncDTO>>> componentFlows = new HashMap();
    Set<String> getMatchedSelectors = new HashSet();
    List<ParmsWorkspace> wsList = new ArrayList();
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(sourceWs), false, client, config);
    for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces()) {
      if (wsSync.getWorkspaceItemId().equals(sourceWs.workspaceItemId))
      {

        for (ComponentSyncDTO compSync : wsSync.getComponents())
        {
          ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetOutgoingRepositoryUrl(), compSync.getTargetOutgoingWorkspaceItemId());
          ParmsWorkspace wsMatched = findWorkspace(wsList, ws);
          
          Map<ComponentSyncDTO, List<BaselineSyncDTO>> compBlMap = null;
          if (wsMatched != null) {
            compBlMap = (Map)componentFlows.get(wsMatched);
          }
          
          if (compBlMap == null) {
            compBlMap = new HashMap();
            componentFlows.put(ws, compBlMap);
            wsList.add(ws);
          }
          
          List<BaselineSyncDTO> outgoingBaselines = compSync.getOutgoingBaselines();
          
          if ((outgoingBaselines != null) && (outgoingBaselines.size() > 0)) {
            List<BaselineSyncDTO> normalized = getMatchedBlList(outgoingBaselines, blSelectorList, getMatchedSelectors, config, wsSync.getRepositoryUrl());
            if (normalized.size() > 1) {
              throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_SPECIFY_ONLY_ONE_BASLELINE_PER_COMPONENT);
            }
            if (normalized.size() == 1) {
              compBlMap.put(compSync, normalized);
            }
          }
        }
        

        break;
      }
    }
    
    blSelectorList.removeAll(getMatchedSelectors);
    
    if (blSelectorList.size() > 0) {
      Set<String> blSelectorSet = new HashSet(blSelectorList);
      printUnresolvedBaselines(blSelectorSet, config);
    }
    
    return componentFlows;
  }
  


  private ParmsDeliverChangeSetsOrBaselines generateChangeSetFlowDeliverParms(ParmsWorkspace targetWs, List<String> selectors)
    throws FileSystemException
  {
    ParmsDeliverChangeSetsOrBaselines csParam = new ParmsDeliverChangeSetsOrBaselines();
    sourceWorkspace = sourceWs;
    targetWorkspace = targetWs;
    
    ChangeSetSyncDTO[] csList = normalizeCsUuidAndAlias(sourceWs, targetWs, selectors, config, client);
    
    List<String> csItemIdList = new LinkedList();
    for (ChangeSetSyncDTO cs : csList) {
      csItemIdList.add(cs.getChangeSetItemId());
    }
    

    Map<Map<String, String>, Set<ChangeSetSyncDTO>> compToCsMap = new HashMap();
    for (ChangeSetSyncDTO syncCs : csList) {
      Map<String, String> compIdToNameMap = new HashMap();
      compIdToNameMap.put(syncCs.getComponentItemId(), syncCs.getComponentName());
      
      if (compToCsMap.get(compIdToNameMap) == null) {
        Set<ChangeSetSyncDTO> csSet = new HashSet();
        compToCsMap.put(compIdToNameMap, csSet);
      }
      
      ((Set)compToCsMap.get(compIdToNameMap)).add(syncCs);
    }
    
    changeSetItemIds = ((String[])csItemIdList.toArray(new String[csItemIdList.size()]));
    
    if (csItemIdList.size() > 0) {
      return csParam;
    }
    
    config.getContext().stdout().println(Messages.DeliverCmd_NO_OUTGOING_CHANGESETS_OR_BASELINES_FOUND);
    return null;
  }
  

  private ParmsDeliverChangeSetsOrBaselines generateBaselineFlowDeliverParms(ParmsWorkspace targetWs, List<String> selectors)
    throws FileSystemException
  {
    ParmsDeliverChangeSetsOrBaselines blParam = new ParmsDeliverChangeSetsOrBaselines();
    sourceWorkspace = sourceWs;
    targetWorkspace = targetWs;
    
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0);
    
    Map<String, String> compMap = new HashMap();
    List<WorkspaceComponentDTO> wsCompDTO = wsDetails.getComponents();
    for (WorkspaceComponentDTO wsComp : wsCompDTO) {
      compMap.put(wsComp.getItemId(), wsComp.getName());
    }
    
    List<BaselineDTO> blList = normalizeBlUuidAndAlias(sourceWs, targetWs, selectors, config, client);
    
    Object blItemIdList = new LinkedList();
    List<String> compItemIdList = new LinkedList();
    for (BaselineDTO bl : blList) {
      ((List)blItemIdList).add(bl.getItemId());
      if (compItemIdList.contains(bl.getComponentItemId())) {
        throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_SPECIFY_ONLY_ONE_BASLELINE_PER_COMPONENT);
      }
      compItemIdList.add(bl.getComponentItemId());
    }
    
    baselineItemIds = ((String[])((List)blItemIdList).toArray(new String[((List)blItemIdList).size()]));
    
    if (((List)blItemIdList).size() > 0) {
      return blParam;
    }
    
    config.getContext().stdout().println(Messages.DeliverCmd_NO_OUTGOING_CHANGESETS_OR_BASELINES_FOUND);
    return null;
  }
  



  private List<ParmsDeliverChangeSetsOrBaselines> generateChangeSetFlowDeliverParms(List<String> selectors)
    throws FileSystemException
  {
    Map<ParmsWorkspace, Map<ComponentSyncDTO, List<ChangeSetSyncDTO>>> componentFlows = 
      getTargetWsCompAndChangeSetSyncMapWithCsSelectors(selectors);
    
    List<ParmsDeliverChangeSetsOrBaselines> csBlParamList = new LinkedList();
    

    for (Map.Entry<ParmsWorkspace, Map<ComponentSyncDTO, List<ChangeSetSyncDTO>>> entry : componentFlows.entrySet()) {
      ParmsDeliverChangeSetsOrBaselines csParam = new ParmsDeliverChangeSetsOrBaselines();
      
      List<ComponentSyncDTO> compList = new LinkedList();
      for (Map.Entry<ComponentSyncDTO, List<ChangeSetSyncDTO>> compCsMapSet : ((Map)entry.getValue()).entrySet()) {
        compList.add((ComponentSyncDTO)compCsMapSet.getKey());
      }
      
      if (compList.size() > 0) {
        if (getKeyworkspaceItemId.equals(sourceWs.workspaceItemId))
        {


          Set<ComponentSyncDTO> allSyncComp = new HashSet();
          for (Object compToCsListMap : componentFlows.values()) {
            allSyncComp.addAll(((Map)compToCsListMap).keySet());
          }
          

          printNonCollaboratedComponents(srcWsDetails.getName(), sourceWs.workspaceItemId, 
            sourceWs.repositoryUrl, allSyncComp, config);
        }
        
        sourceWorkspace = sourceWs;
        targetWorkspace = ((ParmsWorkspace)entry.getKey());
        
        List<String> compUuidList = new LinkedList();
        for (ComponentSyncDTO comp : compList) {
          compUuidList.add(comp.getComponentItemId());
        }
        
        Object csList = getCsListFromCsCompMapList((Map)entry.getValue());
        unresolvedCsList = new ArrayList();
        unresolvedCsList.addAll((Collection)csList);
        
        Object csParamList = new LinkedList();
        for (ChangeSetSyncDTO syncCs : (List)csList) {
          ((List)csParamList).add(syncCs.getChangeSetItemId());
        }
        
        if (((List)csParamList).size() > 0)
        {
          changeSetItemIds = ((String[])((List)csParamList).toArray(new String[((List)csParamList).size()]));
          csBlParamList.add(csParam);
        }
      }
    }
    
    return csBlParamList;
  }
  
  private List<ParmsDeliverChangeSetsOrBaselines> generateBaselineFlowDeliverParms(List<String> selectors)
    throws FileSystemException
  {
    Map<ParmsWorkspace, Map<ComponentSyncDTO, List<BaselineSyncDTO>>> componentFlows = 
      getTargetWsCompAndBaselineSyncMapWithBlSelectors(selectors);
    
    List<ParmsDeliverChangeSetsOrBaselines> baselnParamList = new LinkedList();
    

    for (Map.Entry<ParmsWorkspace, Map<ComponentSyncDTO, List<BaselineSyncDTO>>> entry : componentFlows.entrySet()) {
      ParmsDeliverChangeSetsOrBaselines blParam = new ParmsDeliverChangeSetsOrBaselines();
      
      List<ComponentSyncDTO> compList = new LinkedList();
      for (Map.Entry<ComponentSyncDTO, List<BaselineSyncDTO>> compCsMapSet : ((Map)entry.getValue()).entrySet()) {
        compList.add((ComponentSyncDTO)compCsMapSet.getKey());
      }
      
      if (compList.size() > 0) {
        if (getKeyworkspaceItemId.equals(sourceWs.workspaceItemId))
        {


          Set<ComponentSyncDTO> allSyncComp = new HashSet();
          for (Object compToCsListMap : componentFlows.values()) {
            allSyncComp.addAll(((Map)compToCsListMap).keySet());
          }
          

          printNonCollaboratedComponents(srcWsDetails.getName(), sourceWs.workspaceItemId, 
            sourceWs.repositoryUrl, allSyncComp, config);
        }
        
        sourceWorkspace = sourceWs;
        targetWorkspace = ((ParmsWorkspace)entry.getKey());
        
        List<BaselineSyncDTO> blList = getBlListFromBlCompMapList((Map)entry.getValue());
        
        Object blParamList = new LinkedList();
        for (BaselineSyncDTO syncBl : blList) {
          ((List)blParamList).add(syncBl.getBaselineItemId());
        }
        
        if (((List)blParamList).size() > 0)
        {
          baselineItemIds = ((String[])((List)blParamList).toArray(new String[((List)blParamList).size()]));
          baselnParamList.add(blParam);
        }
      }
    }
    
    return baselnParamList;
  }
  









  private ParmsDeliverComponents generateComponentFlowDeliverParms(ParmsWorkspace targetWs, List<String> selectors, boolean hierarchicalDeliver)
    throws FileSystemException
  {
    List<WorkspaceComponentDTO> syncCompList = getSyncCompList(targetWs, new HashSet(selectors));
    
    ParmsDeliverComponents compParam = new ParmsDeliverComponents();
    targetWorkspace = targetWs;
    sourceWorkspace = sourceWs;
    sourceWorkspaceExplicit = true;
    hierarchicalDeliver = hierarchicalDeliver;
    

    List<String> compList = new LinkedList();
    for (WorkspaceComponentDTO syncComp : syncCompList) {
      compList.add(syncComp.getItemId());
    }
    
    componentItemIds = ((String[])compList.toArray(new String[compList.size()]));
    
    return compParam;
  }
  








  private List<ParmsDeliverComponents> generateComponentFlowDeliverParms(List<String> selectors, boolean hierarchicalDeliver)
    throws FileSystemException
  {
    Map<ParmsWorkspace, Set<ComponentSyncDTO>> componentFlows = getTargetWsCompAndChangeSetSyncMapWithCompSelectors(
      sourceWs, selectors);
    

    List<ParmsDeliverComponents> compParamList = new LinkedList();
    
    for (Map.Entry<ParmsWorkspace, Set<ComponentSyncDTO>> entry : componentFlows.entrySet()) {
      ParmsDeliverComponents compParam = new ParmsDeliverComponents();
      
      Set<ComponentSyncDTO> comps = (Set)entry.getValue();
      if (comps.size() > 0) {
        List<String> compList = new LinkedList();
        for (ComponentSyncDTO syncComp : comps) {
          compList.add(syncComp.getComponentItemId());
        }
        
        targetWorkspace = ((ParmsWorkspace)entry.getKey());
        sourceWorkspace = sourceWs;
        componentItemIds = ((String[])compList.toArray(new String[compList.size()]));
        sourceWorkspaceExplicit = false;
        hierarchicalDeliver = hierarchicalDeliver;
        

        compParamList.add(compParam);
      }
    }
    
    if (compParamList.size() == 0) {
      throw StatusHelper.ambiguousSelector(Messages.DeliverCmd_REMOTE_WORKSPACE_NOT_FOUND);
    }
    
    return compParamList;
  }
  

  private List<ParmsDeliverComponents> generateDefaultFlowDeliverParms()
    throws FileSystemException
  {
    return generateComponentFlowDeliverParms(null, false);
  }
  



  private ParmsDeliverWorkspace generateDefaultFlowDeliverParm(ParmsWorkspace targetWs)
    throws FileSystemException
  {
    ParmsDeliverWorkspace wsParam = new ParmsDeliverWorkspace();
    
    sourceWorkspace = sourceWs;
    targetWorkspace = targetWs;
    
    return wsParam;
  }
  


  private void generateDeliverParms(ICommandLine subcmd, ParmsDeliver parms)
    throws FileSystemException
  {
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    Mode mode = Mode.UNSET;
    
    int modeCount = 0;
    if (subcmd.hasOption(DeliverCmdOptions.OPT_MODE_COMPONENTS)) {
      mode = Mode.COMPONENT;
      modeCount++;
    }
    
    if (subcmd.hasOption(DeliverCmdOptions.OPT_MODE_CHANGESETS)) {
      mode = Mode.CS;
      modeCount++;
    }
    
    if (subcmd.hasOption(DeliverCmdOptions.OPT_MODE_BASELINES)) {
      mode = Mode.BASELINE;
      modeCount++;
    }
    
    if (subcmd.hasOption(DeliverCmdOptions.OPT_MODE_WORKITEMS)) {
      mode = Mode.WI;
      modeCount++;
    }
    
    if (modeCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_USE_SINGLE_MODE_ARGUMENT, 
        new String[] { DeliverCmdOptions.OPT_MODE_COMPONENTS.getName(), DeliverCmdOptions.OPT_MODE_CHANGESETS.getName(), 
        DeliverCmdOptions.OPT_MODE_BASELINES.getName(), DeliverCmdOptions.OPT_MODE_WORKITEMS.getName() }));
    }
    

    if ((subcmd.hasOption(DeliverCmdOptions.OPT_MODE_WORKITEMS)) && (subcmd.hasOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        DeliverCmdOptions.OPT_MODE_WORKITEMS.getName(), CommonOptions.OPT_STREAM_TARGET_SELECTOR.getName()));
    }
    

    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    

    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if ((subcmd.hasOption(DeliverCmdOptions.OPT_OVERWRITE_UNCOMMITTED_DEPRECATED)) || (subcmd.hasOption(DeliverCmdOptions.OPT_IGNORE_UNCOMMITTED))) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    

    deliveryRequiresHistoryReorderingDilemmaHandler = new ParmsDeliveryRequiresHistoryReorderingDilemmaHandler();
    if (subcmd.hasOption(DeliverCmdOptions.OPT_HARMONIZE_HISTORY)) {
      deliveryRequiresHistoryReorderingDilemmaHandler.deliveryRequiresHistoryReordering = "continue";
    } else {
      deliveryRequiresHistoryReorderingDilemmaHandler.deliveryRequiresHistoryReordering = "cancel";
    }
    

    deliverDilemmaHandler = new ParmsDeliverDilemmaHandler();
    deliverDilemmaHandler.flowComponents = "no";
    

    deliverDilemmaHandler.flowToNonDefault = "continue";
    
    if (subcmd.hasOption(CommonOptions.OPT_MULTIPLE_PARTICIPANTS)) {
      deliverDilemmaHandler.multipleParticipantsDirection = "continue";
    } else {
      deliverDilemmaHandler.multipleParticipantsDirection = "cancel";
    }
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    IScmCommandLineArgument sourceSelector = ScmCommandLineArgument.create(subcmd.getOptionValue(CommonOptions.OPT_STREAM_SOURCE_SELECTOR, null), config);
    SubcommandUtil.validateArgument(sourceSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository srcRepo = null;
    

    if (sourceSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      

      if (wsInSandboxList.size() == 0) {
        throw StatusHelper.misconfiguredLocalFS(Messages.Common_WS_NOT_FOUND);
      }
      if (wsInSandboxList.size() > 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(
          Messages.DeliverCmd_TOO_MANY_WORKSPACES, CommonOptions.OPT_STREAM_SOURCE_SELECTOR.getName()));
      }
      ISandboxWorkspace wsInSandbox = (ISandboxWorkspace)wsInSandboxList.iterator().next();
      sourceWs = new ParmsWorkspace(RepoUtil.getRepoUri(config, client, 
        wsInSandbox.getRepositoryId(), Collections.singletonList(wsInSandbox)), 
        wsInSandbox.getWorkspaceItemId());
      srcRepo = RepoUtil.login(config, client, config.getConnectionInfo(sourceWs.repositoryUrl));
    }
    else {
      srcRepo = RepoUtil.loginUrlArgAncestor(config, client, sourceSelector);
      IWorkspace ws = RepoUtil.getWorkspace(sourceSelector.getItemSelector(), true, true, srcRepo, config);
      
      sourceWs = new ParmsWorkspace(srcRepo.getRepositoryURI(), ws.getItemId().getUuidValue());
    }
    srcWsDetails = ((WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0));
    

    IScmCommandLineArgument targetSelector = ScmCommandLineArgument.create(subcmd.getOptionValue(CommonOptions.OPT_STREAM_TARGET_SELECTOR, null), config);
    SubcommandUtil.validateArgument(targetSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ParmsWorkspace targetWs = null;
    if (targetSelector != null) {
      ITeamRepository targetRepo = RepoUtil.loginUrlArgAncestor(config, client, targetSelector);
      IWorkspace ws = RepoUtil.getWorkspace(targetSelector.getItemSelector(), true, true, targetRepo, config);
      targetWs = new ParmsWorkspace(targetRepo.getRepositoryURI(), ws.getItemId().getUuidValue());
    } else {
      RepoUtil.loginToWsCollabRepos(Collections.singletonList(sourceWs), client, config, true);
    }
    

    List<String> selectors = new LinkedList();
    List<IScmCommandLineArgument> argSelectors = new LinkedList();
    if (subcmd.hasOption(DeliverCmdOptions.OPT_SELECTORS))
    {
      argSelectors = ScmCommandLineArgument.createList(subcmd.getOptionValues(DeliverCmdOptions.OPT_SELECTORS), config, mode != Mode.WI);
      selectors = RepoUtil.getSelectors(argSelectors);
    }
    


    if ((sourceSelector == null) && (mode == Mode.UNSET) && (selectors.size() == 0))
    {
      Map<String, String> compList = RepoUtil.getComponentsInSandbox(null, client, config);
      

      if (compList.size() > 0) {
        selectors = new ArrayList(compList.keySet());
      }
      
      mode = Mode.COMPONENT;
    }
    



    if ((mode == Mode.UNSET) && (selectors.size() == 0))
    {




      if (targetWs == null) {
        List<ParmsDeliverComponents> compParamList = generateDefaultFlowDeliverParms();
        deliverComponents = ((ParmsDeliverComponents[])compParamList.toArray(new ParmsDeliverComponents[compParamList.size()]));
      }
      else {
        ParmsDeliverWorkspace wsParam = new ParmsDeliverWorkspace();
        sourceWorkspace = sourceWs;
        targetWorkspace = targetWs;
        
        deliverWorkspaces = new ParmsDeliverWorkspace[] { wsParam };
      }
    } else if (mode == Mode.BASELINE)
    {




      if (selectors.size() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_NO_BASELINES_SPECIFIED, DeliverCmdOptions.OPT_MODE_BASELINES.toString()));
      }
      
      SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.BASELINE);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.BASELINE, argSelectors, srcRepo, config);
      
      List<String> blSelectors = RepoUtil.getSelectors(argSelectors);
      

      if (targetWs == null) {
        List<ParmsDeliverChangeSetsOrBaselines> csBlParamList = generateBaselineFlowDeliverParms(blSelectors);
        deliverChangeSetsOrBaselines = ((ParmsDeliverChangeSetsOrBaselines[])csBlParamList.toArray(new ParmsDeliverChangeSetsOrBaselines[csBlParamList.size()]));
      }
      else {
        ParmsDeliverChangeSetsOrBaselines csBlParam = generateBaselineFlowDeliverParms(targetWs, blSelectors);
        deliverChangeSetsOrBaselines = new ParmsDeliverChangeSetsOrBaselines[] { csBlParam };
      }
    }
    else if ((mode == Mode.UNSET) || (mode == Mode.CS))
    {




      if (selectors.size() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_NO_CHANGESETS_SPECIFIED, DeliverCmdOptions.OPT_MODE_CHANGESETS.toString()));
      }
      
      SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.CHANGESET);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, argSelectors, srcRepo, config);
      

      if (targetWs == null) {
        List<ParmsDeliverChangeSetsOrBaselines> csBlParamList = 
          generateChangeSetFlowDeliverParms(selectors);
        deliverChangeSetsOrBaselines = ((ParmsDeliverChangeSetsOrBaselines[])csBlParamList.toArray(
          new ParmsDeliverChangeSetsOrBaselines[csBlParamList.size()]));
      }
      else {
        ParmsDeliverChangeSetsOrBaselines csBlParam = generateChangeSetFlowDeliverParms(
          targetWs, selectors);
        
        deliverChangeSetsOrBaselines = new ParmsDeliverChangeSetsOrBaselines[] { csBlParam };
      }
    } else if (mode == Mode.COMPONENT)
    {




      if (selectors.size() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_NO_COMPONENTS_SPECIFIED, DeliverCmdOptions.OPT_MODE_COMPONENTS.toString()));
      }
      
      SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.COMPONENT);
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, argSelectors, srcRepo, config);
      
      boolean hierarchicalDeliver = config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_COMPONENT_HIERARCHY);
      
      if (targetWs == null)
      {








        List<ParmsDeliverComponents> compParamList = generateComponentFlowDeliverParms(selectors, hierarchicalDeliver);
        deliverComponents = ((ParmsDeliverComponents[])compParamList.toArray(new ParmsDeliverComponents[compParamList.size()]));
      }
      else {
        ParmsDeliverComponents compParam = generateComponentFlowDeliverParms(targetWs, selectors, hierarchicalDeliver);
        deliverComponents = new ParmsDeliverComponents[] { compParam };
      }
    } else if (mode == Mode.WI)
    {
      if (selectors.size() == 0) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_NO_WORKITEMS_SPECIFIED, DeliverCmdOptions.OPT_MODE_WORKITEMS.toString()));
      }
      
      SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.WORKITEM);
      

      deliverChangeSetsOrBaselines = getWorkitemDeliverParms(argSelectors, sourceWs, 
        client, config, srcRepo, subcmd);
    } else {
      throw StatusHelper.internalError(Messages.DeliverCmd_UNEXPECTED_SELECTOR_TYPE);
    }
    
    if (!config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_RELEASE_LOCK)) {
      autoReleaseLocks = Boolean.valueOf(((ScmClientConfiguration)config).getPersistentPreferences().getReleaseLockForDeliver());
    }
    else {
      autoReleaseLocks = Boolean.valueOf(Boolean.parseBoolean(config.getSubcommandCommandLine().getOption(CommonOptions.OPT_RELEASE_LOCK).toString()));
    }
  }
  


  private ParmsDeliverChangeSetsOrBaselines[] getWorkitemDeliverParms(List<IScmCommandLineArgument> argSelectors, ParmsWorkspace sourceWs, IFilesystemRestClient client, IScmClientConfiguration config, ITeamRepository repo, ICommandLine subcmd)
    throws FileSystemException
  {
    ChangeSetUtil csUtil = new ChangeSetUtil();
    csUtil.validateRepoAndLogin(argSelectors, client, config, subcmd, repo == null);
    
    List<WorkItemHierarchyNodeDTO> wiHierarchyList = new ArrayList();
    Map<String, ChangeSetSyncDTO> csList = csUtil.getChangeSetsFromWorkitem(argSelectors, repo, 
      client, config, false, wiHierarchyList);
    if ((csList == null) || (csList.size() == 0)) {
      config.getContext().stdout().println(Messages.ChangesetLocateCmd_CS_NOT_FOUND_FOR_WI);
      StatusHelper.nothingToDeliver();
    }
    


    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(sourceWs), false, client, config);
    
    Map<String, ComponentSyncDTO> compIdToDTO = new HashMap();
    for (WorkspaceSyncDTO wsSync : syncView.getWorkspaces()) {
      if (wsSync.getWorkspaceItemId().equals(workspaceItemId)) {
        for (ComponentSyncDTO compSync : wsSync.getComponents()) {
          if (!compSync.getTargetOutgoingWorkspaceItemId().equals(workspaceItemId)) {
            compIdToDTO.put(compSync.getComponentItemId(), compSync);
          }
        }
      }
    }
    

    if (compIdToDTO.keySet().size() == 0) {
      config.getContext().stdout().println(Messages.DeliverCmd_NO_TARGET);
      StatusHelper.nothingToDeliver();
    }
    

    Map<String, ParmsWorkspace> wsIdToParmsWorkspace = new HashMap();
    Object targetWsToCsList = new HashMap();
    ChangeSetSyncDTO outgoingCsDTO;
    for (ChangeSetSyncDTO csDTO : csList.values())
    {
      ComponentSyncDTO compDTO = (ComponentSyncDTO)compIdToDTO.get(csDTO.getComponentItemId());
      if (compDTO != null) {
        for (Iterator localIterator3 = compDTO.getOutgoingChangeSetsAfterBasis().iterator(); localIterator3.hasNext();) { outgoingCsDTO = (ChangeSetSyncDTO)localIterator3.next();
          if (csDTO.getChangeSetItemId().equals(outgoingCsDTO.getChangeSetItemId()))
          {
            ParmsWorkspace targetWs = (ParmsWorkspace)wsIdToParmsWorkspace.get(compDTO.getTargetOutgoingWorkspaceItemId());
            if (targetWs == null) {
              targetWs = new ParmsWorkspace(compDTO.getTargetOutgoingRepositoryUrl(), 
                compDTO.getTargetOutgoingWorkspaceItemId());
              wsIdToParmsWorkspace.put(compDTO.getTargetOutgoingWorkspaceItemId(), targetWs);
            }
            
            List<String> csToDeliverList = (List)((Map)targetWsToCsList).get(targetWs);
            if (csToDeliverList == null) {
              csToDeliverList = new ArrayList();
              ((Map)targetWsToCsList).put(targetWs, csToDeliverList);
            }
            csToDeliverList.add(csDTO.getChangeSetItemId());
          }
        }
      }
    }
    

    if (((Map)targetWsToCsList).keySet().size() == 0) {
      config.getContext().stdout().println(Messages.DeliverCmd_NOTHING_TO_DELIVER_WI);
      StatusHelper.nothingToDeliver();
    }
    

    ParmsDeliverChangeSetsOrBaselines[] csParams = 
      new ParmsDeliverChangeSetsOrBaselines[((Map)targetWsToCsList).keySet().size()];
    int index = 0;
    for (Map.Entry<ParmsWorkspace, List<String>> entry : ((Map)targetWsToCsList).entrySet()) {
      ParmsDeliverChangeSetsOrBaselines csParam = new ParmsDeliverChangeSetsOrBaselines();
      sourceWorkspace = sourceWs;
      targetWorkspace = ((ParmsWorkspace)entry.getKey());
      changeSetItemIds = ((String[])((List)entry.getValue()).toArray(new String[((List)entry.getValue()).size()]));
      csParams[(index++)] = csParam;
    }
    
    return csParams;
  }
  
  private String getComponentSelector(String componentUuid)
  {
    String componentSelector = "";
    List<WorkspaceComponentDTO> workspaceComponents = srcWsDetails.getComponents();
    for (WorkspaceComponentDTO workspaceComponent : workspaceComponents) {
      if (workspaceComponent.getItemId().equals(componentUuid)) {
        componentSelector = AliasUtil.selector(workspaceComponent.getName(), UUID.valueOf(componentUuid), 
          srcWsDetails.getRepositoryURL(), RepoUtil.ItemType.COMPONENT);
        break;
      }
    }
    return componentSelector;
  }
  
  public void run() throws FileSystemException
  {
    client = SubcommandUtil.setupDaemon(config);
    
    ICommandLine subcmd = config.getSubcommandCommandLine();
    boolean verbose = subcmd.hasOption(CommonOptions.OPT_VERBOSE);
    boolean quiet = subcmd.hasOption(CommonOptions.OPT_QUIET);
    config.setEnableJSON(subcmd.hasOption(CommonOptions.OPT_JSON));
    

    ParmsDeliver parms = new ParmsDeliver();
    generateDeliverParms(subcmd, parms);
    
    if (config.isDryRun()) {
      return;
    }
    SyncReportInappropriateException syncReportInappropriate;
    GapException gap;
    try
    {
      structuredResultOptions = new ParmsStructuredResultOptions();
      result = client.postDeliver(parms, null);
    } catch (TeamRepositoryException e) { DeliverResultDTO result;
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      ItemNotFoundException notFound = (ItemNotFoundException)SubcommandUtil.findExceptionByType(ItemNotFoundException.class, e);
      if (notFound != null) {
        throw StatusHelper.ambiguousSelector(Messages.DeliverCmd_REMOTE_WORKSPACE_NOT_FOUND);
      }
      
      StreamLockedException streamLocked = (StreamLockedException)SubcommandUtil.findExceptionByType(StreamLockedException.class, e);
      if (streamLocked != null) {
        throw StatusHelper.streamLocked(Messages.DeliverCmd_0);
      }
      
      StaleDataException stale = (StaleDataException)SubcommandUtil.findExceptionByType(StaleDataException.class, e);
      if (stale != null) {
        throw StatusHelper.gap(Messages.DeliverCmd_WORKSPACE_IS_STALE);
      }
      
      syncReportInappropriate = (SyncReportInappropriateException)SubcommandUtil.findExceptionByType(SyncReportInappropriateException.class, e);
      if (syncReportInappropriate != null) {
        throw StatusHelper.gap(Messages.DeliverCmd_WRONG_SYNC_REPORT);
      }
      
      gap = (GapException)SubcommandUtil.findExceptionByType(GapException.class, e);
      if (gap != null) {
        throw StatusHelper.gap(Messages.DeliverCmd_WOULD_CREATE_GAP + " " + Messages.DeliverCmd_HINT_ON_GAP);
      }
      
      DeliveryIntroducesConflictsException conflict = (DeliveryIntroducesConflictsException)SubcommandUtil.findExceptionByType(DeliveryIntroducesConflictsException.class, e);
      if (conflict != null) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, AcceptCmd.class);
        
        throw StatusHelper.conflict(NLS.bind(Messages.DeliverCmd_INTRODUCES_CONFLICTS, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString() }));
      }
      
      OutstandingConflictsException conflicted = (OutstandingConflictsException)SubcommandUtil.findExceptionByType(OutstandingConflictsException.class, e);
      if (conflicted != null) {
        throw StatusHelper.conflict(Messages.DeliverCmd_REMOTE_WORKSPACE_HAS_CONFLICTS);
      }
      
      ChangeSetAlreadyInHistoryException csInHistory = (ChangeSetAlreadyInHistoryException)SubcommandUtil.findExceptionByType(ChangeSetAlreadyInHistoryException.class, e);
      if (csInHistory != null) {
        throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_ALREADY_IN_HISTORY);
      }
      
      ChangeSetsNotOutgoingException csNotOutgoing = (ChangeSetsNotOutgoingException)SubcommandUtil.findExceptionByType(ChangeSetsNotOutgoingException.class, e);
      if (csNotOutgoing != null) {
        throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_NOT_OUTGOING);
      }
      
      throw StatusHelper.wrap(Messages.DeliverCmd_FAILED, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    DeliverResultDTO result;
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    StringBuilder builder;
    if (result.isCancelled())
    {
      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.DeliverCmd_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), DeliverCmdOptions.OPT_IGNORE_UNCOMMITTED.getName()));
      }
      

      List<DeliveryRequiresHistoryReorderingDTO> deliveryReqHistoryReorderingList = result.getDeliveryRequiresHistoryReordering();
      if (deliveryReqHistoryReorderingList.size() > 0) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.DeliverCmd_REQUIRES_HISTORY_REORDERING1);
        for (DeliveryRequiresHistoryReorderingDTO deliveryReqHistoryReordering : deliveryReqHistoryReorderingList) {
          err.indent().println(getComponentSelector(deliveryReqHistoryReordering.getComponentItemId()));
        }
        throw StatusHelper.deliveryRequiresHistoryReordering(NLS.bind(Messages.DeliverCmd_REQUIRES_HISTORY_REORDERING2, DeliverCmdOptions.OPT_HARMONIZE_HISTORY.getName()));
      }
      
      if (!result.getComponentsWithConflictingTargets().isEmpty())
      {
        List<ComponentSyncDTO> componentsWithConflictingTargets = new ArrayList(result.getComponentsWithConflictingTargets());
        Collections.sort(componentsWithConflictingTargets, new PendingChangesUtil.ComponentSyncDTOComparator());
        
        StringBuilder builder = new StringBuilder();
        builder.append(Messages.AcceptCmd_INCOMPATIBLE_COMPONENT_FLOW_TARGETS);
        for (ComponentSyncDTO syncDto : componentsWithConflictingTargets) {
          builder.append(NLS.bind(Messages.DeliverCmd_INCOMPATIBLE_SELECTORS, new String[] {
            AliasUtil.selector(syncDto.getLocalWorkspaceName(), UUID.valueOf(syncDto.getLocalWorkspaceItemId()), syncDto.getLocalRepositoryUrl(), syncDto.isIslocalStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE), 
            AliasUtil.selector(syncDto.getComponentName(), UUID.valueOf(syncDto.getComponentItemId()), syncDto.getTargetOutgoingRepositoryUrl(), RepoUtil.ItemType.COMPONENT), 
            AliasUtil.selector(syncDto.getTargetOutgoingWorkspaceName(), UUID.valueOf(syncDto.getTargetOutgoingWorkspaceItemId()), syncDto.getTargetOutgoingRepositoryUrl(), syncDto.isIsTargetOutgoingStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE) }));
        }
        
        throw StatusHelper.createException(builder.toString(), 58, null);
      }
      
      if (!result.getComponentsWithMultipleParticipants().isEmpty())
      {
        List<ComponentSyncDTO> componentsWithMultipleParticpants = new ArrayList(result.getComponentsWithMultipleParticipants());
        Collections.sort(componentsWithMultipleParticpants, new PendingChangesUtil.ComponentSyncDTOComparator());
        
        builder = new StringBuilder();
        builder.append(Messages.AcceptCmd_INCOMPATIBLE_COMPONENT_MULTIPLE_PARTICPANT);
        for (ComponentSyncDTO syncDto : componentsWithMultipleParticpants) {
          builder.append(NLS.bind(Messages.DeliverCmd_INCOMPATIBLE_SELECTORS, new String[] {
            AliasUtil.selector(syncDto.getLocalWorkspaceName(), UUID.valueOf(syncDto.getLocalWorkspaceItemId()), syncDto.getLocalRepositoryUrl(), syncDto.isIslocalStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE), 
            AliasUtil.selector(syncDto.getComponentName(), UUID.valueOf(syncDto.getComponentItemId()), syncDto.getTargetOutgoingRepositoryUrl(), RepoUtil.ItemType.COMPONENT), 
            AliasUtil.selector(syncDto.getTargetOutgoingWorkspaceName(), UUID.valueOf(syncDto.getTargetOutgoingWorkspaceItemId()), syncDto.getTargetOutgoingRepositoryUrl(), syncDto.isIsTargetOutgoingStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE) }));
        }
        
        throw StatusHelper.createException(builder.toString(), 59, null);
      }
    } else if (result.getStructuredResult().size() == 0) {
      List<DeliveryRequiresHistoryReorderingDTO> deliveryReqHistoryReorderingList = result.getDeliveryRequiresHistoryReordering();
      ISubcommandDefinition defnTemp3;
      if (deliveryReqHistoryReorderingList.size() > 0) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.DeliverCmd_CONFLICTS_AFTER_HISTORY_REORDERING1);
        for (DeliveryRequiresHistoryReorderingDTO deliveryReqHistoryReordering : deliveryReqHistoryReorderingList) {
          err.indent().println(getComponentSelector(deliveryReqHistoryReordering.getComponentItemId()));
        }
        
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
        defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
        
        throw StatusHelper.conflict(Messages.DeliverCmd_CONFLICTS_AFTER_HISTORY_REORDERING2 + 
          " " + NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString(), 
          SubcommandUtil.getExecutionString(defnTemp3).toString() }));
      }
      if (!result.getLocksWereHeld().isEmpty()) {
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, LockListCmd.class);
        
        throw StatusHelper.itemLockedInStream(NLS.bind(Messages.DeliverCmd_ITEM_LOCKED_IN_STREAM, config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString()));
      }
      

      if (result.isComponentsToFlow())
      {

        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.DeliverCmd_COMPONENT_NOT_IN_FLOWTARGET);
        
        ArrayList<String> csId = new ArrayList();
        
        if (unresolvedCsList != null) {
          for (ChangeSetSyncDTO changeset : unresolvedCsList) {
            csId.add(changeset.getChangeSetItemId());
          }
        }
        
        ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(csId, 
          false, srcWsDetails.getItemId(), "workspace", srcWsDetails.getRepositoryURL(), client, config);
        Set<String> compAliasName = new HashSet();
        String compAlias;
        for (int i = 0; i < csDTOList.length; i++) {
          compAlias = AliasUtil.selector(csDTOList[i].getComponentName(), UUID.valueOf(csDTOList[i].getComponentItemId()), srcWsDetails.getRepositoryURL(), RepoUtil.ItemType.COMPONENT);
          compAliasName.add(compAlias);
        }
        
        for (String compAlias : compAliasName) {
          err.indent().println(compAlias);
        }
        
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, HelpCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, AddComponentsCmd.class);
        
        err.println(NLS.bind(Messages.DeliverCmd_ADD_COMPONENT_TO_FLOWTARGET, new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), 
          SubcommandUtil.getExecutionString(defnTemp2).toString() }));

      }
      else if (config.isJSONEnabled()) {
        config.getContext().stdout().print(new JSONObject());
      } else {
        out.println(Messages.DeliverCmd_NOTHING_TO_DELIVER);
      }
      
      StatusHelper.nothingToDeliver();
    }
    else
    {
      if (!quiet) {
        if (!config.isJSONEnabled()) {
          out.println(Messages.DeliverCmd_DELIVER_MESSAGE);
        }
        AcceptResultDisplayer.showResult(client, result, verbose, config, out.indent());
      }
      
      if (!config.isJSONEnabled()) {
        out.println(Messages.DeliverCmd_SUCCESSFUL);
      }
    }
  }
}
