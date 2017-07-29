package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineCustomAttributes;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;





public class BaselineUnsetCustomAttributeCmd
{
  public BaselineUnsetCustomAttributeCmd() {}
  
  public static void unsetCustomAttributes(IScmCommandLineArgument blSelector, IScmCommandLineArgument compSelector, String[] unsetCustomAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, blSelector);
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    

    IBaseline bl = RepoUtil.getBaseline(blSelector.getItemSelector(), comp != null ? comp.getItemId().getUuidValue() : null, 
      comp != null ? comp.getName() : null, repo, client, config);
    

    unsetCustomAttributes(bl, unsetCustomAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.BaselineUnsetCustomAttributesCmd_PROPERTY_UNSET_SUCCESS);
  }
  
  private static void unsetCustomAttributes(IBaseline bl, String[] unsetCustomAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsBaselineCustomAttributes parms = new ParmsBaselineCustomAttributes();
    baseline = new ParmsBaseline();
    baseline.baselineItemId = bl.getItemId().getUuidValue();
    baseline.repositoryUrl = repo.getRepositoryURI();
    
    unsetCustomAttrs = unsetCustomAttrs;
    
    try
    {
      client.postSetBaselineCustomAttributes(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.BaselineUnsetCustomAttributesCmd_PROPERTY_UNSET_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
}
