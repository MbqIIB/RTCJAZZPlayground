package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentCustomAttributes;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;





public class ComponentUnsetCustomAttributeCmd
{
  public ComponentUnsetCustomAttributeCmd() {}
  
  public static void unsetCustomAttributes(IScmCommandLineArgument compSelector, String[] unsetCustomAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
    

    IComponent comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    

    unsetCustomAttributes(comp, unsetCustomAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.ComponentUnsetCustomAttributesCmd_PROPERTY_UNSET_SUCCESS);
  }
  
  private static void unsetCustomAttributes(IComponent comp, String[] unsetCustomAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsComponentCustomAttributes parms = new ParmsComponentCustomAttributes();
    componentUuid = comp.getItemId().getUuidValue();
    repositoryUrl = repo.getRepositoryURI();
    
    unsetCustomAttrs = unsetCustomAttrs;
    
    try
    {
      client.postSetComponentCustomAttributes(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ComponentUnsetCustomAttributesCmd_PROPERTY_UNSET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
