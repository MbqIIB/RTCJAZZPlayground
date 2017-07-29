package com.ibm.team.filesystem.cli.client.util;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.workitems.IFileSystemWorkItemRestClient;
import com.ibm.team.filesystem.client.workitems.rest.parameters.ParmsWorkItem;
import com.ibm.team.filesystem.client.workitems.rest.parameters.ParmsWorkItemHierarchy;
import com.ibm.team.filesystem.common.internal.rest.client.core.ChangeSetDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyNodeDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyRootDTO;
import com.ibm.team.filesystem.common.workitems.internal.rest.WorkItemDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.workitem.common.model.IWorkItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;




public class ChangeSetUtil
{
  public Map<String, ITeamRepository> repoStrToRepo = new HashMap();
  
  public ChangeSetUtil() {}
  
  public void validateRepoAndLogin(List<IScmCommandLineArgument> selectors, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli, boolean errorOnNoRepo) throws FileSystemException {
    for (IScmCommandLineArgument selector : selectors) {
      if (selector.getRepositorySelector() == null) {
        if (errorOnNoRepo) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetLocateCmd_UNABLE_TO_DETERMINE_REPO, 
            selector.getItemSelector(), cli.getDefinition().getOption(CommonOptions.OPT_URI).getName()));

        }
        


      }
      else if (!repoStrToRepo.keySet().contains(selector.getRepositorySelector()))
      {



        for (Map.Entry<String, ITeamRepository> entry : repoStrToRepo.entrySet()) {
          if (RepoUtil.isRepoUriSame(((ITeamRepository)entry.getValue()).getRepositoryURI(), selector.getRepositorySelector(), config)) {
            repoStrToRepo.put(selector.getRepositorySelector(), (ITeamRepository)entry.getValue());
            break;
          }
        }
        

        ITeamRepository repo = RepoUtil.login(config, client, config.getConnectionInfo(selector.getRepositorySelector()));
        repoStrToRepo.put(selector.getRepositorySelector(), repo);
      }
    }
  }
  
  public Map<String, ChangeSetSyncDTO> getChangeSetsFromWorkitem(List<IScmCommandLineArgument> selectors, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config, boolean includeChildWorkitems) throws FileSystemException
  {
    List<WorkItemHierarchyNodeDTO> wiHierarchyList = new ArrayList();
    return getChangeSetsFromWorkitem(selectors, repo, client, config, includeChildWorkitems, wiHierarchyList);
  }
  
  public Map<String, ChangeSetSyncDTO> getChangeSetsFromWorkitem(List<IScmCommandLineArgument> selectors, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config, boolean includeChildWorkitems, List<WorkItemHierarchyNodeDTO> wiHierarchyList)
    throws FileSystemException
  {
    Map<ITeamRepository, List<ParmsWorkItem>> repoToWiList = new HashMap();
    
    List<ParmsWorkItem> wiSpecifiedList = new ArrayList();
    ParmsWorkItem wiParm; for (IScmCommandLineArgument selector : selectors)
    {
      try
      {
        wiNumber = Integer.parseInt(selector.getItemSelector());
      } catch (NumberFormatException localNumberFormatException) { int wiNumber;
        throw StatusHelper.argSyntax(NLS.bind(Messages.ChangesetAssociateWorkitemCmd_2, selector.getItemSelector()));
      }
      int wiNumber;
      ITeamRepository wiRepo = repo;
      if (selector.getRepositorySelector() != null) {
        wiRepo = (ITeamRepository)repoStrToRepo.get(selector.getRepositorySelector());
      }
      

      workItem = RepoUtil.findWorkItem(wiNumber, wiRepo, config);
      

      List<ParmsWorkItem> wiList = (List)repoToWiList.get(wiRepo);
      if (wiList == null) {
        wiList = new ArrayList();
        repoToWiList.put(wiRepo, wiList);
      }
      wiParm = new ParmsWorkItem(workItem.getItemId().getUuidValue(), wiRepo.getRepositoryURI());
      wiList.add(wiParm);
      wiSpecifiedList.add(wiParm);
    }
    
    IFileSystemWorkItemRestClient wiClient = (IFileSystemWorkItemRestClient)SubcommandUtil.startDaemon(IFileSystemWorkItemRestClient.class, 
      config.getSandboxes(), config);
    
    Object csIdToDTO = new HashMap();
    List<WorkItemHierarchyNodeDTO> wiResultHierarchyList = new ArrayList();
    for (Map.Entry<ITeamRepository, List<ParmsWorkItem>> entry : repoToWiList.entrySet()) {
      Map<String, ChangeSetSyncDTO> csIdToDTOTemp = getChangeSetsFromWorkItem((ITeamRepository)entry.getKey(), (List)entry.getValue(), 
        wiClient, client, config, includeChildWorkitems, wiResultHierarchyList);
      
      ((Map)csIdToDTO).putAll(csIdToDTOTemp);
    }
    

    for (IWorkItem workItem = wiSpecifiedList.iterator(); workItem.hasNext(); 
        wiParm.hasNext())
    {
      ParmsWorkItem wi = (ParmsWorkItem)workItem.next();
      wiParm = wiResultHierarchyList.iterator(); continue;WorkItemHierarchyNodeDTO wiHierarchy = (WorkItemHierarchyNodeDTO)wiParm.next();
      if (wiHierarchy.getWorkItem().getItemId().equals(workItemId)) {
        wiHierarchyList.add(wiHierarchy);
      }
    }
    

    return csIdToDTO;
  }
  


  private Map<String, ChangeSetSyncDTO> getChangeSetsFromWorkItem(ITeamRepository repo, List<ParmsWorkItem> wiList, IFileSystemWorkItemRestClient wiClient, IFilesystemRestClient client, IScmClientConfiguration config, boolean includeChildWorkitems, List<WorkItemHierarchyNodeDTO> wiHierarchyList)
    throws FileSystemException
  {
    ParmsWorkItemHierarchy parms = new ParmsWorkItemHierarchy();
    repositoryUrl = repo.getRepositoryURI();
    workItems = ((ParmsWorkItem[])wiList.toArray(new ParmsWorkItem[wiList.size()]));
    if (includeChildWorkitems) {
      searchDepth = -1;
    }
    
    WorkItemHierarchyRootDTO result = null;
    try {
      result = wiClient.getWorkItemHierarchy(parms, null);
      wiHierarchyList.addAll(result.getChildren());
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetLocateCmd_UNABLE_TO_FETCH_WORKITEM_HIERARCHY, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    Map<String, ChangeSetSyncDTO> csIdToDTO = new HashMap();
    if (result != null)
    {
      List<ChangeSetDTO> csList = new ArrayList();
      getChangeSets(result, csList);
      
      List<String> csIds = new ArrayList();
      for (ChangeSetDTO cs : csList) {
        if (!csIds.contains(cs.getItemId())) {
          csIds.add(cs.getItemId());
        }
      }
      

      csIdToDTO = getChangeSetsFromRepo(csIds, repo, client, config);
    }
    
    return csIdToDTO;
  }
  
  private void getChangeSets(WorkItemHierarchyDTO wiHierarchy, List<ChangeSetDTO> csList)
  {
    List<WorkItemHierarchyDTO> wiHierarchyList = wiHierarchy.getChildren();
    if (wiHierarchyList != null) {
      for (WorkItemHierarchyDTO wiH : wiHierarchyList) {
        getChangeSets(wiH, csList);
      }
    }
    

    List<ChangeSetDTO> csListForWi = wiHierarchy.getChangeSets();
    if ((csListForWi != null) && (csListForWi.size() > 0)) {
      csList.addAll(csListForWi);
    }
  }
  
  public Map<String, ChangeSetSyncDTO> getChangeSets(List<IScmCommandLineArgument> csSelectors, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<ITeamRepository, List<String>> repoToCsSelectors = new HashMap();
    ITeamRepository csRepo;
    for (IScmCommandLineArgument csSelector : csSelectors) {
      csRepo = repo;
      if (csSelector.getRepositorySelector() != null) {
        csRepo = (ITeamRepository)repoStrToRepo.get(csSelector.getRepositorySelector());
      }
      
      List<String> csList = (List)repoToCsSelectors.get(csRepo);
      if (csList == null) {
        csList = new ArrayList();
        repoToCsSelectors.put(csRepo, csList);
      }
      csList.add(csSelector.getItemSelector());
    }
    

    Map<String, ChangeSetSyncDTO> csList = new HashMap();
    for (Object entry : repoToCsSelectors.entrySet()) {
      Map<String, ChangeSetSyncDTO> csListTemp = getChangeSetsFromRepo((List)((Map.Entry)entry).getValue(), (ITeamRepository)((Map.Entry)entry).getKey(), client, 
        config);
      
      csList.putAll(csListTemp);
    }
    
    return csList;
  }
  
  private Map<String, ChangeSetSyncDTO> getChangeSetsFromRepo(List<String> csSelectors, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    ChangeSetSyncDTO[] csDTOList = RepoUtil.findChangeSets(csSelectors, false, null, null, repo.getRepositoryURI(), 
      client, config);
    Map<String, ChangeSetSyncDTO> csList = null;
    if (csDTOList != null) {
      csList = new HashMap(csDTOList.length);
      for (ChangeSetSyncDTO csDTO : csDTOList) {
        if (!csList.containsKey(csDTO.getChangeSetItemId())) {
          csList.put(csDTO.getChangeSetItemId(), csDTO);
        }
      }
    }
    

    List<String> csNotFoundList = new ArrayList(csSelectors);
    Object csAlias; if (csList != null) {
      Object csIterator = csNotFoundList.iterator();
      while (((Iterator)csIterator).hasNext()) {
        String csSelector = (String)((Iterator)csIterator).next();
        csAlias = RepoUtil.lookupUuidAndAlias(csSelector, repo.getRepositoryURI());
        if (csList.keySet().contains(((IUuidAliasRegistry.IUuidAlias)csAlias).getUuid().getUuidValue())) {
          ((Iterator)csIterator).remove();
        }
      }
    }
    

    if (csNotFoundList.size() > 0) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.Common_CS_NOT_FOUND_HEADER);
      err = err.indent();
      
      for (csAlias = csNotFoundList.iterator(); ((Iterator)csAlias).hasNext();) { String csSelector = (String)((Iterator)csAlias).next();
        err.println(csSelector);
      }
      
      throw StatusHelper.itemNotFound(Messages.Common_CS_NOT_FOUND);
    }
    
    return csList;
  }
}
