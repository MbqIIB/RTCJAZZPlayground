package com.ibm.team.filesystem.cli.client.internal.users;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributorRecord;
import com.ibm.team.repository.common.IContributorRecordHandle;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.StaleDataException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.internal.ScmContributorUtil;
import java.util.List;
import org.eclipse.osgi.util.NLS;



public class SetUserCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public SetUserCmd() {}
  
  public static final IOptionKey OPT_USER_ALIAS = new OptionKey("selector");
  
  public static final IOptionKey OPT_USER_ID = new OptionKey("userId");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(new PositionalOptionDefinition(OPT_USER_ALIAS, "selector", 1, 1, "@"), Messages.SetUserCmd_USER_ALIAS)
      .addOption(new PositionalOptionDefinition(OPT_USER_ID, "userId", 1, 1), Messages.SetUserCmd_USER_ID);
    
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IScmCommandLineArgument cmdLineArg = ScmCommandLineArgument.create(cli.getOptionValue(OPT_USER_ALIAS), config);
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, cmdLineArg);
    
    String selector = cmdLineArg.getItemSelector();
    IUuidAliasRegistry.IUuidAlias uuidAlias = RepoUtil.lookupUuidAndAlias(selector);
    if (uuidAlias == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.SetUserCmd_USER_NOT_FOUND, selector));
    }
    
    IContributorRecordHandle crh = (IContributorRecordHandle)IContributorRecord.ITEM_TYPE.createItemHandle(uuidAlias.getUuid(), null);
    
    IContributorRecord cr = null;
    try
    {
      cr = (IContributorRecord)repo.itemManager().fetchCompleteItem(crh, 1, null);
    }
    catch (ItemNotFoundException localItemNotFoundException)
    {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.SetUserCmd_USER_NOT_FOUND, selector));
    } catch (TeamRepositoryException localTeamRepositoryException1) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.SetUserCmd_TRE, selector));
    }
    
    if (!cr.getUserIds().isEmpty()) {
      throw StatusHelper.failure(Messages.SetUserCmd_USER_ID_ALREADY_SET, null);
    }
    
    String userId = cli.getOption(OPT_USER_ID);
    try
    {
      ScmContributorUtil.setUserId(repo, cr, userId, null);
    } catch (StaleDataException localStaleDataException) {
      throw StatusHelper.staleData(Messages.SetUserCmd_USER_ID_EXISTS);
    } catch (PermissionDeniedException localPermissionDeniedException) {
      throw StatusHelper.permissionFailure(Messages.SetUserCmd_PERMISSION_DENIED);
    } catch (TeamRepositoryException localTeamRepositoryException2) {
      throw StatusHelper.failure(Messages.SetUserCmd_COULD_NOT_SET_THE_USER_ID, null);
    }
    
    new IndentingPrintStream(config.getContext().stdout()).println(Messages.SetUserCmd_SUCCESSFUL);
  }
}
