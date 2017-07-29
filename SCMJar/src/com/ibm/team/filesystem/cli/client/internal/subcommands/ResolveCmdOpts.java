package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import org.eclipse.osgi.util.NLS;












public class ResolveCmdOpts
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPT_TO_RESOLVE = new PositionalOptionDefinition("path", 0, -1);
  

  public static final NamedOptionDefinition OPT_RESOLVE_PROPOSED = new NamedOptionDefinition("p", "proposed", 0);
  

  public static final NamedOptionDefinition OPT_RESOLVE_AUTO = new NamedOptionDefinition("a", "auto-merge", 0);
  

  public static final NamedOptionDefinition OPT_RESOLVE_MERGED = new NamedOptionDefinition("c", "checkedin", 0);
  

  public static final NamedOptionDefinition OPT_RESOLVE_EXTERNAL_COMPARE = new NamedOptionDefinition("x", "xcompare", 1);
  

  public static final NamedOptionDefinition OPT_RESOLVE_MOVE = new NamedOptionDefinition("m", "move", 1);
  

  public static final NamedOptionDefinition OPT_RESOLVE_LOCAL = new NamedOptionDefinition("l", "local", 0);
  
  public ResolveCmdOpts() {}
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    options.setLongHelp(Messages.ResolveCmdOpts_0);
    
    OPT_TO_RESOLVE.setShowAsRequired();
    
    options
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS, false)
      .addOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED, Messages.Common_FORCE_OVERWRITE_UNCOMMITTED, false)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_RESOLVE_PROPOSED, Messages.ResolveCmdOpts_2, true)
      .addOption(OPT_RESOLVE_MERGED, Messages.ResolveCmdOpts_4, true))
      .addOption(OPT_TO_RESOLVE, Messages.ResolveCmdOpts_OPT_TO_RESOLVE, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS, false)
      .addOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1, false)
      .addOption(OPT_RESOLVE_AUTO, Messages.ResolveCmdOpts_AUTOMERGE, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(OPT_RESOLVE_EXTERNAL_COMPARE, NLS.bind(Messages.LogoutCmd_CONCATENATE, 
      Messages.ResolveCmdOpts_OPT_TO_COMPARE, Messages.DiffCmdOpts_EXTERNAL_COMPARE), true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(OPT_RESOLVE_MOVE, NLS.bind(Messages.LogoutCmd_CONCATENATE, 
      Messages.ResolveCmdOpts_1, Messages.ResolveCmdOpts_3), true)
      .addOption(OPT_TO_RESOLVE, Messages.ResolveCmdOpts_OPT_TO_RESOLVE, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_RESOLVE_PROPOSED, Messages.ResolveCmdOpts_2, true)
      .addOption(OPT_RESOLVE_MERGED, Messages.ResolveCmdOpts_4, true)
      .addOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1, false))
      .addOption(OPT_RESOLVE_LOCAL, Messages.ResolveCmdOpts_OPT_RESOLVE_LOCAL, true)
      .addOption(OPT_TO_RESOLVE, Messages.ResolveCmdOpts_OPT_TO_RESOLVE, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(OPT_RESOLVE_AUTO, Messages.ResolveCmdOpts_AUTOMERGE, true)
      .addOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER, Messages.AcceptCmdOptions_1, false)
      .addOption(OPT_RESOLVE_LOCAL, Messages.ResolveCmdOpts_OPT_RESOLVE_LOCAL, true)
      .addOption(OPT_TO_RESOLVE, Messages.ResolveCmdOpts_OPT_TO_RESOLVE, true))
      .addOption(new ContinuousGroup()
      .addOption(CommonOptions.OPT_URI, CommonOptions.OPT_URI_HELP, false)
      .addOption(SubcommandUtil.getCredentialsGroup(false))
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_ROOT_HELP, false)
      .addOption(OPT_RESOLVE_LOCAL, Messages.ResolveCmdOpts_OPT_RESOLVE_LOCAL, true)
      .addOption(OPT_RESOLVE_EXTERNAL_COMPARE, NLS.bind(Messages.LogoutCmd_CONCATENATE, 
      Messages.ResolveCmdOpts_OPT_TO_COMPARE, Messages.DiffCmdOpts_EXTERNAL_COMPARE), true));
    return options;
  }
}
