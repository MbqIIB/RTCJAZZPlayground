package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConfigurationChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConnectionDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.PutWorkspaceResultDTO;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.util.List;
import org.eclipse.osgi.util.NLS;





public class CreateWorkspaceCmd
  extends CreateStreamCmd
{
  public CreateWorkspaceCmd() {}
  
  public void validateArguments(ICommandLine subargs)
    throws FileSystemException
  {
    int contentArgCount = (subargs.hasOption(CreateWorkspaceBaseOptions.OPT_STREAM) ? 1 : 0) + (
      subargs.hasOption(CreateWorkspaceBaseOptions.OPT_SNAPSHOT) ? 1 : 0) + (
      subargs.hasOption(CreateWorkspaceOptions.OPT_EMPTY) ? 1 : 0);
    if (contentArgCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.CreateWorkspaceCmd_4, 
        new Object[] {
        subargs.getDefinition().getOption(CreateWorkspaceOptions.OPT_EMPTY).getName(), 
        subargs.getDefinition().getOption(CreateWorkspaceBaseOptions.OPT_STREAM).getName(), 
        subargs.getDefinition().getOption(CreateWorkspaceBaseOptions.OPT_SNAPSHOT).getName() }));
    }
    


    if ((subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DUPLICATE)) && ((contentArgCount > 0) || (subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DESC)))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.CreateWorkspaceCmd_DUPLICATE_MUTUALLY_EXCLUSIVE, 
        new String[] { CreateWorkspaceBaseOptions.OPT_STREAM.getName(), CreateWorkspaceBaseOptions.OPT_SNAPSHOT.getName(), CreateWorkspaceBaseOptions.OPT_DESC.getName(), 
        CreateWorkspaceOptions.OPT_EMPTY.getName() }));
    }
  }
  
  public boolean isStream()
  {
    return false;
  }
  
  public String getCreationFailureMsg()
  {
    return Messages.CreateWorkspaceCmd_0;
  }
  
  public String getSuccessfulCreationMsg()
  {
    return Messages.CreateWorkspaceCmd_12;
  }
  
  public String getSnapshotUpdateFailureMsg()
  {
    return Messages.CreateWorkspaceCmd_UPDATE_WITH_SNAPSHOT_FAILURE;
  }
  
  protected String getActiveChangeSetErrorMessageNoName()
  {
    return Messages.CreateWorkspaceCmd_UNKNOWN_COMPONENT_HAS_ACTIVE_CHANGES;
  }
  
  protected String getActiveChangeSetErrorMessageWithComponentName()
  {
    return Messages.CreateWorkspaceCmd_ACTIVE_CHANGESETS_IN_COMPONENT;
  }
  
  public String getFlowUpdateFailureMsg()
  {
    return Messages.CreateWorkspaceCmd_FLOW_TARGET_UPDATE_FAILURE;
  }
  
  public IProjectArea getProjectArea(ICommandLine subargs, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    return null;
  }
  
  public ITeamArea getTeamArea(ICommandLine subargs, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    return null;
  }
  

  public void createComponent(WorkspaceDetailsDTO wsDetails, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs, JSONObject wsObj)
    throws FileSystemException
  {
    if (!subargs.hasOption(CreateWorkspaceOptions.OPT_EMPTY)) {
      ParmsPutWorkspace parmsPutWs = new ParmsPutWorkspace();
      workspace = new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId());
      configurationChanges = new ParmsConfigurationChanges();
      configurationChanges.components = new ParmsComponentChange[1];
      
      ParmsComponentChange newComp = new ParmsComponentChange();
      cmd = "addComponent";
      name = NLS.bind(Messages.CreateWorkspaceCmd_13, wsDetails.getName());
      
      configurationChanges.components[0] = newComp;
      
      try
      {
        wsResult = client.postPutWorkspace(parmsPutWs, null);
      } catch (TeamRepositoryException e) { PutWorkspaceResultDTO wsResult;
        throw StatusHelper.wrap(Messages.CreateWorkspaceCmd_DEFAULT_COMP_ADD_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), wsDetails.getRepositoryURL());
      }
      PutWorkspaceResultDTO wsResult;
      if (wsResult.getComponentsAdded().size() != 1) {
        throw StatusHelper.failure(Messages.CreateWorkspaceCmd_DEFAULT_COMP_ADD_FAILURE, null);
      }
      
      ConfigurationDescriptorDTO configDesc = (ConfigurationDescriptorDTO)wsResult.getComponentsAdded().get(0);
      JSONPrintUtil.jsonizeResult(wsObj, RepoUtil.ItemType.COMPONENT, Messages.CreateComponentCommand_ComponentSuccessfullyCreated, name, 
        configDesc.getComponentItemId(), configDesc.getConnection().getRepositoryURL());
    }
  }
}
