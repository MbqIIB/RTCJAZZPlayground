package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import java.io.PrintStream;
import java.util.List;
import org.eclipse.osgi.util.NLS;



public class ChangesetSetCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ChangesetSetCmd() {}
  
  public static final NamedOptionDefinition OPT_COMPLETE = new NamedOptionDefinition(null, "complete", 0);
  public static final NamedOptionDefinition OPT_CURRENT = new NamedOptionDefinition(null, "current", 0);
  public static final NamedOptionDefinition OPT_COMMENT = new NamedOptionDefinition(null, "comment", 1);
  public static final NamedOptionDefinition OPT_SKIP_MERGE_TARGET = new NamedOptionDefinition(null, "skip-merge-targets", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options opts = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(opts);
    
    opts.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(ChangesetCommonOptions.OPT_WORKSPACE_NAME, Messages.ChangesetCompleteCmdOptions_0)
      .addOption(new ContinuousGroup(true)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_COMPLETE, Messages.ChangesetSetCmd_Complete_HELP, false)
      .addOption(OPT_CURRENT, Messages.ChangesetSetCmd_Current_HELP, false))
      .addOption(OPT_COMMENT, Messages.ChangesetSetCmd_Comment_HELP, false))
      .addOption(OPT_SKIP_MERGE_TARGET, Messages.ChangesetCompleteCmdOptions_OPT_SKIP_CURRENT_MERGE)
      .addOption(ChangesetCommonOptions.OPT_CHANGESETS, Messages.ChangesetCompleteCmdOptions_OPT_CHANGESET_HELP);
    
    return opts;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(OPT_COMPLETE)) {
      count++;
    }
    if (cli.hasOption(OPT_CURRENT)) {
      count++;
    }
    if (cli.hasOption(OPT_COMMENT)) {
      count++;
    }
    
    if (count == 0) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ConflictsCmd_1, new String[] {
        OPT_COMPLETE.getName(), OPT_CURRENT.getName(), OPT_COMMENT.getName() }));
    }
    
    if ((cli.hasOption(OPT_COMPLETE)) && (cli.hasOption(OPT_CURRENT))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, new String[] {
        OPT_COMPLETE.getName(), OPT_CURRENT.getName() }));
    }
    
    if ((cli.hasOption(OPT_CURRENT)) && 
      (cli.getOptions(ChangesetCommonOptions.OPT_CHANGESETS.getId()).size() > 1)) {
      throw StatusHelper.argSyntax(Messages.ChangesetSetCmd_ONE_CURRENT_HELP);
    }
    
    int successCount = 0;
    
    try
    {
      if (cli.hasOption(OPT_COMPLETE)) {
        ChangesetCompleteCmd.setComplete(config);
        successCount++;
      }
      
      if (cli.hasOption(OPT_CURRENT)) {
        IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(
          cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
        SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
        
        List<IScmCommandLineArgument> csSelectors = ScmCommandLineArgument.createList(
          cli.getOptionValues(ChangesetCommonOptions.OPT_CHANGESETS.getId()), config);
        SubcommandUtil.validateArgument(csSelectors, RepoUtil.ItemType.CHANGESET);
        
        ChangesetCurrentCmd.setCurrent(wsSelector, (IScmCommandLineArgument)csSelectors.get(0), config);
        successCount++;
      }
      
      if (cli.hasOption(OPT_COMMENT)) {
        String comment = cli.getOption(OPT_COMMENT.getId());
        List<IScmCommandLineArgument> csSelectors = ScmCommandLineArgument.createList(
          cli.getOptionValues(ChangesetCommonOptions.OPT_CHANGESETS.getId()), config);
        
        ChangesetCommentCmd.setComment(comment, csSelectors, config);
        successCount++;
      }
    } finally {
      if (successCount == 1) {
        if (cli.hasOption(OPT_COMPLETE)) {
          config.getContext().stdout().println(NLS.bind(Messages.ChangesetSetCmd_Success1, 
            Messages.ChangesetSetCmd_Complete));
        } else if (cli.hasOption(OPT_CURRENT)) {
          config.getContext().stdout().println(NLS.bind(Messages.ChangesetSetCmd_Success1, 
            Messages.ChangesetSetCmd_Current));
        } else {
          config.getContext().stdout().println(NLS.bind(Messages.ChangesetSetCmd_Success1, 
            Messages.ChangesetSetCmd_Comment));
        }
      } else if (successCount == 2) {
        if (cli.hasOption(OPT_COMPLETE)) {
          config.getContext().stdout().println(NLS.bind(Messages.ChangesetSetCmd_Success2, 
            Messages.ChangesetSetCmd_Comment, Messages.ChangesetSetCmd_Complete));
        } else {
          config.getContext().stdout().println(NLS.bind(Messages.ChangesetSetCmd_Success2, 
            Messages.ChangesetSetCmd_Comment, Messages.ChangesetSetCmd_Current));
        }
      }
    }
  }
}
