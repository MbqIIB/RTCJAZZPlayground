package com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd.LocalConflictToResolve;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.IFileOptions;
import com.ibm.team.filesystem.client.internal.LocalFileStorage;
import com.ibm.team.filesystem.client.internal.api.storage.FileOptionsFactory;
import com.ibm.team.filesystem.client.internal.api.storage.LocalFileAccessExtension;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.FileLineDelimiter;
import com.ibm.team.filesystem.common.IFileContent;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.VersionableChangeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.FilePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.LocalConflictSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.compare.rangedifferencer.IRangeComparator;
import org.eclipse.compare.rangedifferencer.RangeDifference;
import org.eclipse.compare.rangedifferencer.RangeDifferencer;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;














public class InPlaceConflictHandler
  extends AbstractConflictHandler
{
  private static final String TEMPORARY_PREFIX = "TMP";
  private static final String TEMPORARY_EXTENSION = ".tmp";
  public static final String MARKER_MY_SECTION = "<<<<<<< mine";
  public static final String MARKER_PROPOSED = "=======";
  public static final String MARKER_END = ">>>>>>> proposed";
  public InPlaceConflictHandler() {}
  
  public static class LineRangeComparator
    implements IRangeComparator
  {
    private final String[] lines;
    
    public LineRangeComparator(String[] lines)
    {
      this.lines = lines;
    }
    
    public int getRangeCount() {
      return lines.length;
    }
    
    public boolean rangesEqual(int thisIndex, IRangeComparator other, int otherIndex)
    {
      if (thisIndex > getRangeCount()) {
        return false;
      }
      
      if (otherIndex > other.getRangeCount()) {
        return false;
      }
      

      LineRangeComparator otherLrc = (LineRangeComparator)other;
      
      String s1 = lines[thisIndex];
      String s2 = lines[otherIndex];
      
      if ((s1.length() == s2.length()) && (s1.hashCode() == s2.hashCode())) {
        return s1.equals(s2);
      }
      
      return false;
    }
    
    public boolean skipRangeComparison(int length, int maxLength, IRangeComparator other)
    {
      return false;
    }
  }
  












  public void writeDiff(IPath toWrite, BufferedReader ancestorReader, BufferedReader leftReader, BufferedReader rightReader, OutputStreamWriter output)
    throws FileSystemException, IOException
  {
    LineRangeComparator ancestor = streamToComparator(ancestorReader);
    LineRangeComparator left = streamToComparator(leftReader);
    LineRangeComparator right = streamToComparator(rightReader);
    
    Writer writer = new Writer(output);
    

    RangeDifference[] diffs;
    
    if ((left != null) && (right != null)) {
      diffs = RangeDifferencer.findRanges(ancestor, left, right);
    } else {
      if (left != null) {
        writer.write(left);
      } else {
        assert (right != null);
        writer.write(right);
      }
      
      return;
    }
    
    RangeDifference[] diffs;
    for (RangeDifference diff : diffs) {
      switch (diff.kind())
      {
      case 0: 
        writer.write(ancestor, diff.ancestorStart(), diff.ancestorEnd());
        break;
      

      case 3: 
        writer.write(left, diff.leftStart(), diff.leftEnd());
        break;
      

      case 2: 
        writer.write(right, diff.rightStart(), diff.rightEnd());
        break;
      

      case 4: 
        writer.write(left, diff.leftStart(), diff.leftEnd());
        break;
      

      case 1: 
        writer.write("<<<<<<< mine");
        

        writer.write(left, diff.leftStart(), diff.leftEnd());
        
        writeAncestor(writer, ancestor, diff);
        
        writer.write(getMarkerProposed());
        
        writer.write(right, diff.rightStart(), diff.rightEnd());
        
        writer.write(getMarkerEnd());
        break;
      
      case 5: 
      default: 
        throw StatusHelper.failure("Unexpected RangeDifference.kind(): " + diff.kind(), null);
      }
    }
  }
  
  protected String getMarkerProposed() {
    return "=======";
  }
  
  protected String getMarkerEnd() {
    return ">>>>>>> proposed";
  }
  

  protected void writeAncestor(Writer writer, LineRangeComparator ancestor, RangeDifference diff)
    throws IOException
  {}
  

  static final class Writer
  {
    private static final String NEWLINE = System.getProperty("line.separator");
    final OutputStreamWriter out;
    
    public Writer(OutputStreamWriter output) {
      out = output;
    }
    
    public void write(String s) throws IOException {
      out.write(s);
      out.write(NEWLINE);
    }
    
    public void write(InPlaceConflictHandler.LineRangeComparator comp, int start, int end) throws IOException {
      for (int i = start; i < end; i++) {
        write(lines[i]);
      }
    }
    
    public void write(InPlaceConflictHandler.LineRangeComparator comp)
      throws IOException
    {
      for (int i = 0; i < lines.length; i++) {
        write(lines[i]);
      }
    }
  }
  

  private LineRangeComparator streamToComparator(BufferedReader reader)
    throws IOException
  {
    if (reader == null) {
      return null;
    }
    
    ArrayList<String> lines = new ArrayList();
    
    String line;
    while ((line = reader.readLine()) != null) { String line;
      lines.add(line);
    }
    
    return new LineRangeComparator((String[])lines.toArray(new String[lines.size()]));
  }
  







  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, ComponentSyncDTO compSync, ConflictSyncDTO conflictSync, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    if ((!conflictSync.isContentConflict()) || 
      (!conflictSync.getConflictType().equals("modify_modify"))) {
      return;
    }
    
    writeConflict(cfaRoot, ws, compSync.getComponentItemId(), conflictSync.getVersionableItemId(), 
      conflictSync.getVersionableItemType(), conflictSync.getPathHint(), conflictSync.getCommonAncestorVersionableStateId(), 
      conflictSync.getOriginalSelectedContributorVersionableStateId(), conflictSync.getProposedContributorVersionableStateId(), 
      client, config);
  }
  







  protected void writeLocalConflict(IPath cfaRoot, Map.Entry<ParmsWorkspace, List<ResolveCmd.LocalConflictToResolve>> entry, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    for (ResolveCmd.LocalConflictToResolve conflict : (List)entry.getValue()) {
      LocalConflictSyncDTO localConflictItem = conflict.getLocalConflictItem();
      if (localConflictItem.isContentType())
      {

        writeConflict(
          cfaRoot, 
          (ParmsWorkspace)entry.getKey(), 
          conflict.getComponentId(), 
          localConflictItem.getVersionableItemId(), 
          localConflictItem.getVersionableItemType(), 
          localConflictItem.getPathHint(), 
          localConflictItem.getCommonAncestorVersionableStateId(), 
          localConflictItem.getProposedContributorVersionableStateId(), 
          client, 
          config);
      }
    }
  }
  
  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, String componentId, VersionableChangeDTO changeDTO, ChangeSyncDTO changeSync, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    if ((!changeSync.isContentChange()) || (!changeSync.isModifyType())) {
      return;
    }
    
    writeConflict(cfaRoot, ws, componentId, changeDTO.getVersionableItemId(), changeDTO.getVersionableType(), 
      changeDTO.getParentPathHint(), changeSync.getBeforeStateId(), changeDTO.getConfigurationStateId(), changeSync.getAfterStateId(), 
      client, config);
  }
  


  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, String componentId, String versionableId, String versionableType, String pathHint, String ancestorId, String proposedId, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    try
    {
      shareableDTO = RepoUtil.findLocalVersionable(ws, componentId, 
        versionableId, versionableType, client, cfaRoot);
    } catch (TeamRepositoryException localTeamRepositoryException) { ShareableDTO shareableDTO;
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.Common_PATH_NOT_SHARED, pathHint));
    }
    ShareableDTO shareableDTO;
    IPath path = new Path(shareableDTO.getSandboxPath()).append(
      StringUtil.createPathString(shareableDTO.getRelativePath().getSegments()));
    ResourcePropertiesDTO resProp = RepoUtil.getResourceProperties(path.toOSString(), client, config);
    
    if ((!resProp.getVersionableItemType().equals("file")) || 
      (resProp.getFileProperties() == null)) {
      return;
    }
    
    boolean requiresTransform = (LocalFileStorage.getFileAccessExtension().transformsContents()) && 
      (resProp.getUserProperties() != null);
    boolean resetToReadOnly = false;
    File file = new File(path.toOSString());
    try {
      if ((file.exists()) && (!file.canWrite())) {
        resetToReadOnly = file.setWritable(true);
      }
      
      OutputStreamWriter out = null;
      BufferedReader ancestor = null;
      BufferedReader left = null;
      BufferedReader right = null;
      OutputStream outStream = null;
      Charset streamEncoding = null;
      File tempFile = null;
      IPath tempPath;
      try { tempFile = File.createTempFile("TMP", null);
      } catch (IOException localIOException1) {
        tempPath = path.addFileExtension(".tmp");
        tempFile = new File(tempPath.toOSString()); }
      while (tempFile.exists()) {
        tempPath = tempPath.addFileExtension(".tmp");
        tempFile = new File(tempPath.toOSString());
      }
      
      try
      {
        if (requiresTransform)
        {

          outStream = new ByteArrayOutputStream();
        } else {
          outStream = findOutputStreamFor(tempFile);
        }
        if (outStream == null)
        {

























          safeClose(out);
          safeClose(ancestor);
          safeClose(left);
          safeClose(right);return;
        }
        ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
        streamEncoding = getEncodingFor(resProp);
        ancestor = fetchReaderForFileItem(repo, workspaceItemId, componentId, 
          versionableId, ancestorId, config);
        try {
          left = new BufferedReader(new InputStreamReader(new FileInputStream(file), streamEncoding));
        } catch (FileNotFoundException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        }
        right = fetchReaderForFileItem(repo, workspaceItemId, componentId, 
          versionableId, proposedId, config);
        
        Assert.isTrue((left != null) && (right != null));
        
        out = new OutputStreamWriter(outStream, streamEncoding);
        try
        {
          writeDiff(path, ancestor, left, right, out);
        } catch (IOException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        }
        
        safeClose(out); } finally { safeClose(out);
        safeClose(ancestor);
        safeClose(left);
        safeClose(right);
      }
      safeClose(ancestor);
      safeClose(left);
      safeClose(right);
      

      if (requiresTransform)
      {

        OutputStream fileStream = null;
        InputStream in = null;
        try {
          fileStream = findOutputStreamFor(path);
          in = new ByteArrayInputStream(((ByteArrayOutputStream)outStream).toByteArray());
          
          IFileOptions fileInfo = FileOptionsFactory.getFileOptions(false, FileLineDelimiter.LINE_DELIMITER_PLATFORM, streamEncoding.toString(), resProp.getUserProperties());
          in = LocalFileStorage.getFileAccessExtension().prepareContentsToSet(fileInfo, in);
          RepoUtil.transfer(in, fileStream);
        } catch (IOException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        } finally {
          safeClose(fileStream);
          safeClose(in);
        }
      }
      else if (file.delete())
      {

        OutputStream fileStream = null;
        InputStream in = null;
        try {
          fileStream = findOutputStreamFor(path);
          in = new BufferedInputStream(new FileInputStream(tempFile));
          
          IFileOptions fileInfo = FileOptionsFactory.getFileOptions(false, FileLineDelimiter.LINE_DELIMITER_PLATFORM, streamEncoding.toString(), resProp.getUserProperties());
          in = LocalFileStorage.getFileAccessExtension().prepareContentsToSet(fileInfo, in);
          RepoUtil.transfer(in, fileStream);
        } catch (IOException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        } finally {
          safeClose(fileStream);
          safeClose(in);
        }
        tempFile.delete();
      }
    }
    finally {
      if (resetToReadOnly) {
        file.setWritable(false);
      }
    }
    if (resetToReadOnly) {
      file.setWritable(false);
    }
  }
  



  protected void writeConflict(IPath cfaRoot, ParmsWorkspace ws, String componentId, String versionableId, String versionableType, String pathHint, String ancestorId, String mineId, String proposedId, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    try
    {
      shareableDTO = RepoUtil.findLocalVersionable(ws, componentId, 
        versionableId, versionableType, client, cfaRoot);
    } catch (TeamRepositoryException localTeamRepositoryException) { ShareableDTO shareableDTO;
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.Common_PATH_NOT_SHARED, pathHint));
    }
    ShareableDTO shareableDTO;
    IPath path = new Path(shareableDTO.getSandboxPath()).append(
      StringUtil.createPathString(shareableDTO.getRelativePath().getSegments()));
    ResourcePropertiesDTO resProp = RepoUtil.getResourceProperties(path.toOSString(), client, config);
    
    if ((!resProp.getVersionableItemType().equals("file")) || 
      (resProp.getFileProperties() == null)) {
      return;
    }
    
    boolean requiresTransform = (LocalFileStorage.getFileAccessExtension().transformsContents()) && 
      (resProp.getUserProperties() != null);
    boolean resetToReadOnly = false;
    File file = new File(path.toOSString());
    try {
      if ((file.exists()) && (!file.canWrite())) {
        resetToReadOnly = file.setWritable(true);
      }
      
      OutputStreamWriter out = null;
      BufferedReader ancestor = null;
      BufferedReader left = null;
      BufferedReader right = null;
      OutputStream outStream = null;
      Charset streamEncoding = null;
      try
      {
        if (requiresTransform)
        {

          outStream = new ByteArrayOutputStream();
        } else {
          outStream = findOutputStreamFor(path);
        }
        if (outStream == null)
        {






















          safeClose(out);
          safeClose(ancestor);
          safeClose(left);
          safeClose(right);return;
        }
        ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
        ancestor = fetchReaderForFileItem(repo, workspaceItemId, componentId, 
          versionableId, ancestorId, config);
        left = fetchReaderForFileItem(repo, workspaceItemId, componentId, 
          versionableId, mineId, config);
        right = fetchReaderForFileItem(repo, workspaceItemId, componentId, 
          versionableId, proposedId, config);
        
        Assert.isTrue((left != null) && (right != null));
        
        streamEncoding = getEncodingFor(resProp);
        out = new OutputStreamWriter(outStream, streamEncoding);
        try
        {
          writeDiff(path, ancestor, left, right, out);
        } catch (IOException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        }
        
        safeClose(out); } finally { safeClose(out);
        safeClose(ancestor);
        safeClose(left);
        safeClose(right);
      }
      safeClose(ancestor);
      safeClose(left);
      safeClose(right);
      

      if (requiresTransform)
      {

        OutputStream fileStream = null;
        InputStream in = null;
        try {
          fileStream = findOutputStreamFor(path);
          in = new ByteArrayInputStream(((ByteArrayOutputStream)outStream).toByteArray());
          
          IFileOptions fileInfo = FileOptionsFactory.getFileOptions(false, FileLineDelimiter.LINE_DELIMITER_PLATFORM, streamEncoding.toString(), resProp.getUserProperties());
          in = LocalFileStorage.getFileAccessExtension().prepareContentsToSet(fileInfo, in);
          RepoUtil.transfer(in, fileStream);
        } catch (IOException e) {
          throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_3, path.toOSString()), e);
        } finally {
          safeClose(fileStream);
          safeClose(in);
        }
      }
    } finally {
      if (resetToReadOnly) {
        file.setWritable(false);
      }
    }
    if (resetToReadOnly) {
      file.setWritable(false);
    }
  }
  
  private Charset getEncodingFor(ResourcePropertiesDTO resProp)
  {
    if (resProp.getFileProperties() != null) {
      String encoding = resProp.getFileProperties().getEncoding();
      return getEncoding(encoding);
    }
    
    return Charset.defaultCharset();
  }
  
  private Charset getEncoding(String encoding) {
    if ((encoding != null) && (encoding.length() > 0)) {
      try {
        if (encoding != null) {
          return Charset.forName(encoding);
        }
      }
      catch (IllegalCharsetNameException localIllegalCharsetNameException) {}catch (UnsupportedCharsetException localUnsupportedCharsetException) {}
    }
    

    return Charset.defaultCharset();
  }
  







  private BufferedReader fetchReaderForFileItem(ITeamRepository repo, String wsId, String compId, String itemId, String stateId, IScmClientConfiguration config)
    throws FileSystemException
  {
    if ((itemId == null) || (stateId == null)) {
      return null;
    }
    

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    BufferedReader reader = null;
    try
    {
      RepoUtil.httpDownloadFile(repo, wsId, compId, itemId, stateId, bos, config);
      

      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      String itemType = IFileItem.ITEM_TYPE.getNamespaceURI() + "." + IFileItem.ITEM_TYPE.getName();
      VersionableDTO ver = RepoUtil.getVersionableById(scmService, wsId, compId, itemId, stateId, itemType, config);
      
      Charset cs = Charset.defaultCharset();
      if ((ver.getVersionable() instanceof IFileItem)) {
        IFileItem fileItem = (IFileItem)ver.getVersionable();
        if (fileItem.getContent() != null) {
          cs = getEncoding(fileItem.getContent().getCharacterEncoding());
        }
      }
      

      reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bos.toByteArray()), cs));
    } finally {
      safeClose(bos);
    }
    
    return reader;
  }
  






  private OutputStream findOutputStreamFor(IPath filePath)
    throws FileSystemException
  {
    try
    {
      return new BufferedOutputStream(new FileOutputStream(filePath.toFile()));
    } catch (FileNotFoundException e) {
      throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_4, filePath.toOSString()), e);
    }
  }
  





  private OutputStream findOutputStreamFor(File file)
    throws FileSystemException
  {
    try
    {
      return new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      throw StatusHelper.failure(NLS.bind(Messages.InPlaceConflictHandler_4, file.getAbsolutePath()), e);
    }
  }
  


  private final void safeClose(Closeable toClose)
  {
    if (toClose != null) {
      try {
        toClose.close();
      }
      catch (IOException localIOException) {}
    }
  }
}
