package com.ibm.team.filesystem.cli.client.internal.delete;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeleteContent;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IClientConfiguration;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IVersionedContentClaimerData;
import com.ibm.team.scm.common.VersionedContentClaimedByMultipleItems;
import com.ibm.team.scm.common.internal.dto2.VersionedContentClaimedInMultipleItemsData;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;






public class DeleteStateContentCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public DeleteStateContentCmd() {}
  
  public static final IPositionalOptionDefinition OPT_ITEM = new PositionalOptionDefinition("item", 1, 1, "@");
  
  public static final IPositionalOptionDefinition OPT_STATES = new PositionalOptionDefinition("states", 1, -1, "@");
  public static final NamedOptionDefinition OPT_FORCE = new NamedOptionDefinition(null, "force", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options.addOption(OPT_FORCE, Messages.DeleteStateContentCmd_OPT_FORCE_HELP)
      .addOption(OPT_ITEM, Messages.DeleteStateContentCmd_OPT_ITEM_HELP)
      .addOption(OPT_STATES, Messages.DeleteStateContentCmd_OPT_STATES_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument itemSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_ITEM.getId()), config);
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, itemSelector);
    SubcommandUtil.validateArgument(itemSelector, RepoUtil.ItemType.VERSIONABLE);
    

    String itemId = null;
    IUuidAliasRegistry.IUuidAlias itemAlias = RepoUtil.lookupUuidAndAlias(itemSelector.getItemSelector(), repo.getRepositoryURI());
    if (itemAlias == null)
    {
      ResourcePropertiesDTO itemResource = getResource(itemSelector.getItemSelector(), client, config);
      if (itemResource == null) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_PATH_NOT_SHARED, itemSelector.getItemSelector()));
      }
      
      itemId = itemResource.getItemId();
    } else {
      itemId = itemAlias.getUuid().getUuidValue();
    }
    

    List<IScmCommandLineArgument> stateSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_STATES.getId()), config);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.VERSIONABLE, stateSelectors, repo, config);
    SubcommandUtil.validateArgument(stateSelectors, RepoUtil.ItemType.VERSIONABLE);
    
    Map<String, String> states = new HashMap(stateSelectors.size());
    for (IScmCommandLineArgument stateSelector : stateSelectors) {
      IUuidAliasRegistry.IUuidAlias stateAlias = RepoUtil.lookupUuidAndAlias(stateSelector.getItemSelector(), repo.getRepositoryURI());
      if (stateAlias != null) {
        if (!states.containsKey(stateAlias.getUuid().getUuidValue())) {
          states.put(stateAlias.getUuid().getUuidValue(), stateSelector.getItemSelector());
        }
      } else {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_INVALID_ALIAS_UUID, stateSelector.getItemSelector()));
      }
    }
    
    boolean force = cli.hasOption(OPT_FORCE);
    

    deleteStates(itemId, states, repo, force, client, config);
    
    config.getContext().stdout().println(Messages.DeleteStateContentCmd_SUCCESS);
  }
  
  private ResourcePropertiesDTO getResource(String itemSelector, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    ILocation itemPath = SubcommandUtil.makeAbsolutePath(config, itemSelector);
    
    File itemFile = new File(itemPath.toOSString());
    if (!itemFile.exists()) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, itemSelector));
    }
    

    RepoUtil.getSandboxPathsAndRegister(itemPath.toOSString(), client, config);
    
    ParmsResourceProperties parms = new ParmsResourceProperties(false, new String[] { itemPath.toOSString() });
    ResourcesDTO resourcesDTO = null;
    try {
      resourcesDTO = client.getResourceProperties(parms, null);
    } catch (TeamRepositoryException localTeamRepositoryException) {
      throw StatusHelper.propertiesUnavailable(NLS.bind(Messages.HistoryCmd_UNABLE_TO_GET_PROPERTY, itemSelector));
    }
    
    if (resourcesDTO != null) {
      List<ResourcePropertiesDTO> resourceProperties = resourcesDTO.getResourceProperties();
      ResourcePropertiesDTO dto = (ResourcePropertiesDTO)resourceProperties.get(0);
      if ((dto != null) && (dto.getShare() != null)) {
        return dto;
      }
    }
    
    return null;
  }
  
  private void deleteStates(String itemId, Map<String, String> states, ITeamRepository repo, boolean forceDelete, IFilesystemRestClient client, IClientConfiguration config) throws FileSystemException
  {
    List<String> deletedStates = new ArrayList();
    
    for (Map.Entry<String, String> entry : states.entrySet()) {
      ParmsDeleteContent parms = new ParmsDeleteContent();
      repositoryUrl = repo.getRepositoryURI();
      itemId = itemId;
      stateId = ((String)entry.getKey());
      force = forceDelete;
      try
      {
        client.postDeleteContent(parms, null);
        deletedStates.add((String)entry.getValue());
      } catch (TeamRepositoryException e) {
        if (deletedStates.size() > 0) {
          IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
          out.println(Messages.DeleteStateContentCmd_PARTIAL_SUCCESS);
          for (String state : deletedStates) {
            out.indent().println(state);
          }
        }
        
        if ((e instanceof VersionedContentClaimedByMultipleItems)) {
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
          VersionedContentClaimedByMultipleItems multiClaim = (VersionedContentClaimedByMultipleItems)e;
          if (multiClaim.getData() != null) {
            VersionedContentClaimedInMultipleItemsData multiItemsData = multiClaim.getData();
            err.println(NLS.bind(Messages.DeleteStateContentCmd_MULTI_ITEM_CLAIM, entry.getValue(), Integer.valueOf(multiItemsData.getTotalClaimers())));
            for (IVersionedContentClaimerData data : multiItemsData.getClaimerData()) {
              err.indent().println(data.getApproximatePath());
            }
            if (multiItemsData.getTotalClaimers() > multiItemsData.getClaimerData().size()) {
              err.indent().println("...");
            }
            err.println(NLS.bind(Messages.DeleteStateContentCmd_MULTI_ITEM_CLAIM_ACTION, OPT_FORCE.getName()));
          }
          
          throw StatusHelper.orphan(Messages.DeleteStateContentCmd_FAILURE, e);
        }
        
        throw StatusHelper.wrap(Messages.DeleteStateContentCmd_FAILURE, e, 
          new IndentingPrintStream(config.getContext().stderr()));
      }
    }
  }
}
