# -*-ruby-*-

jnetcap = BuildEnv::ALPINE['jnetcap']
jvector = BuildEnv::ALPINE['jvector']
mvvm = BuildEnv::ALPINE['mvvm']

jts = []

## Bootstrap
jts << JarTarget.buildTarget(mvvm, Jars::Base, 'bootstrap', "#{ALPINE_HOME}/mvvm/bootstrap")

## API
jts << (jt = JarTarget.buildTarget(mvvm, Jars::Base, 'api', ["#{ALPINE_HOME}/mvvm/api", '../version']))
BuildEnv::ALPINE.installTarget.installJars(jt, mvvm.getWebappDir('webstart'), nil, true)

## Local API
jts << JarTarget.buildTarget(mvvm, Jars::Base + [ mvvm['api']], 'localapi', "#{ALPINE_HOME}/mvvm/localapi")

## Reporting
deps  = Jars::Base + Jars::Jasper + Jars::JFreeChart + [ mvvm['api']]
jts << JarTarget.buildTarget(mvvm, deps, 'reporting', "#{ALPINE_HOME}/mvvm/reporting")

## Implementation
deps  = Jars::Base + Jars::TomcatEmb + Jars::JavaMail + Jars::Jcifs +
  Jars::Dom4j + Jars::Activation + Jars::Trove + Jars::Jasper + Jars::JFreeChart +
  [ mvvm['bootstrap'], mvvm['api'], mvvm['localapi'], mvvm['reporting'], jnetcap['impl'], jvector['impl']]

jts << JarTarget.buildTarget(mvvm, deps, 'impl', "#{ALPINE_HOME}/mvvm/impl")

# servlets
ServletBuilder.new(mvvm, 'com.untangle.mvvm.invoker.jsp',
                   "#{ALPINE_HOME}/mvvm/servlets/http-invoker", [], [], [],
                   [BuildEnv::SERVLET_COMMON])

deps = %w( commons-httpclient-3.0/commons-httpclient-3.0.jar
           commons-codec-1.3/commons-codec-1.3.jar
           commons-fileupload-1.1/commons-fileupload-1.1.jar
         ).map { |n| ThirdpartyJar.get("#{BuildEnv::DOWNLOADS}/#{n}") }
ServletBuilder.new(mvvm, 'com.untangle.mvvm.store.jsp',
                   "#{ALPINE_HOME}/mvvm/servlets/onlinestore", deps)

ServletBuilder.new(mvvm, 'com.untangle.mvvm.reports.jsp',
                   "#{ALPINE_HOME}/mvvm/servlets/reports")

# wmi installer
ServletBuilder.new(mvvm, "com.untangle.mvvm.user.servlet","mvvm/servlets/wmi", [])


deps = FileList["#{BuildEnv::DOWNLOADS}/Ajax/jars/*jar"].exclude(/.*servlet-api.jar/).map { |n| ThirdpartyJar.get(n) }
ms = [ MoveSpec.new("#{BuildEnv::DOWNLOADS}/Ajax/WebRoot/js", '**/*', 'AjaxTk')]
ServletBuilder.new(mvvm, 'com.untangle.mvvm.root.jsp',
                   "#{ALPINE_HOME}/mvvm/servlets/ROOT", deps, [], ms)

ajaxTkList =
  [
   'core/AjxCore.js',
   'core/AjxEnv.js',
   'util/AjxUtil.js',
   'util/AjxText.js',
   'core/AjxException.js',
   'util/AjxCookie.js',
   'soap/AjxSoapException.js',
   'soap/AjxSoapFault.js',
   'soap/AjxSoapDoc.js',
   'net/AjxRpcRequest.js',
   'net/AjxRpc.js',
   'util/AjxWindowOpener.js',
   'util/AjxVector.js',
   'util/AjxStringUtil.js',
   'debug/AjxDebug.js',
   'debug/AjxDebugXmlDocument.js',
   'xml/AjxXmlDoc.js',
   'core/AjxEnv.js',
   'core/AjxImg.js',
   'core/AjxException.js',
   'util/AjxCallback.js',
   'util/AjxTimedAction.js',
   'events/AjxEvent.js',
   'events/AjxEventMgr.js',
   'events/AjxListener.js',
   'util/AjxDateUtil.js',
   'util/AjxStringUtil.js',
   'util/AjxVector.js',
   'util/AjxSelectionManager.js',
   'net/AjxPost.js',
   'util/AjxBuffer.js',
   'util/AjxCache.js',
   'dwt/core/DwtImg.js',
   'dwt/core/Dwt.js',
   'dwt/core/DwtException.js',
   'dwt/core/DwtDraggable.js',
   'dwt/core/DwtDragTracker.js',
   'dwt/graphics/DwtCssStyle.js',
   'dwt/graphics/DwtPoint.js',
   'dwt/graphics/DwtRectangle.js',
   'dwt/graphics/DwtUnits.js',
   'dwt/events/DwtEvent.js',
   'dwt/events/DwtEventManager.js',
   'dwt/events/DwtDateRangeEvent.js',
   'dwt/events/DwtDisposeEvent.js',
   'dwt/events/DwtUiEvent.js',
   'dwt/events/DwtControlEvent.js',
   'dwt/events/DwtKeyEvent.js',
   'dwt/events/DwtMouseEvent.js',
   'dwt/events/DwtMouseEventCapture.js',
   'dwt/events/DwtListViewActionEvent.js',
   'dwt/events/DwtSelectionEvent.js',
   'dwt/events/DwtHtmlEditorStateEvent.js',
   'dwt/events/DwtTreeEvent.js',
   'dwt/events/DwtHoverEvent.js',
   'dwt/dnd/DwtDragEvent.js',
   'dwt/dnd/DwtDragSource.js',
   'dwt/dnd/DwtDropEvent.js',
   'dwt/dnd/DwtDropTarget.js',
   'dwt/widgets/DwtHoverMgr.js',
   'dwt/widgets/DwtControl.js',
   'dwt/widgets/DwtComposite.js',
   'dwt/widgets/DwtShell.js',
   'dwt/widgets/DwtColorPicker.js',
   'dwt/widgets/DwtBaseDialog.js',
   'dwt/widgets/DwtDialog.js',
   'dwt/widgets/DwtLabel.js',
   'dwt/widgets/DwtListView.js',
   'dwt/widgets/DwtButton.js',
   'dwt/widgets/DwtMenuItem.js',
   'dwt/widgets/DwtMenu.js',
   'dwt/widgets/DwtMessageDialog.js',
   'dwt/widgets/DwtHtmlEditor.js',
   'dwt/widgets/DwtInputField.js',
   'dwt/widgets/DwtSash.js',
   'dwt/widgets/DwtToolBar.js',
   'dwt/graphics/DwtBorder.js',
   'dwt/widgets/DwtToolTip.js',
   'dwt/widgets/DwtStickyToolTip.js',
   'dwt/widgets/DwtTreeItem.js',
   'dwt/widgets/DwtTree.js',
   'dwt/widgets/DwtCalendar.js',
   'dwt/widgets/DwtPropertyPage.js',
   'dwt/widgets/DwtTabView.js',
   'dwt/widgets/DwtWizardDialog.js',
   'dwt/widgets/DwtSelect.js',
   'dwt/widgets/DwtAddRemove.js',
   'dwt/widgets/DwtAlert.js',
   'dwt/widgets/DwtText.js',
   'dwt/widgets/DwtIframe.js',
   'dwt/xforms/DwtXFormDialog.js',
   'dwt/widgets/DwtPropertySheet.js',
   'dwt/widgets/DwtGrouper.js',
   'dwt/widgets/DwtProgressBar.js',
   'dwt/events/DwtXFormsEvent.js',
   'dwt/xforms/XFormGlobal.js',
   'dwt/xforms/XModel.js',
   'dwt/xforms/XModelItem.js',
   'dwt/xforms/XForm.js',
   'dwt/xforms/XFormItem.js',
   'dwt/xforms/XFormChoices.js',
   'dwt/xforms/OSelect_XFormItem.js',
   'dwt/xforms/ButtonGrid.js',
].map { |e| "#{BuildEnv::DOWNLOADS}/Ajax/WebRoot/js/#{e}" }

bundledAjx = "#{mvvm.getWebappDir("ROOT")}/AjaxTk/BundledAjx.js"

file bundledAjx => ajaxTkList do
  File.open(bundledAjx, 'w') do |o|
    ajaxTkList.each do |e|
      File.open(e, 'r') do |i|
        i.each_line { |l| o.puts(l) }
      end
    end
  end

  Kernel.system('sed', '-i', '-e', '/\/\/if (AjxEnv.isGeckoBased)/,+1s/\/\///', bundledAjx);
end
BuildEnv::ALPINE.installTarget.registerDependency(bundledAjx)

ServletBuilder.new(mvvm, 'com.untangle.mvvm.sessiondumper.jsp',
                   "#{ALPINE_HOME}/mvvm/servlets/session-dumper")

ms = MoveSpec.new("#{ALPINE_HOME}/mvvm/hier", FileList["#{ALPINE_HOME}/mvvm/hier/**/*"], mvvm.distDirectory)
cf = CopyFiles.new(mvvm, ms, 'hier', BuildEnv::ALPINE.filterset)
mvvm.registerTarget('hier', cf)

BuildEnv::ALPINE.installTarget.installJars(jts, "#{mvvm.distDirectory}/usr/share/metavize/lib",
                           nil, false, true)

BuildEnv::ALPINE.installTarget.installJars(Jars::Base, "#{mvvm.distDirectory}/usr/share/java/mvvm")

BuildEnv::ALPINE.installTarget.installJars(Jars::Reporting, "#{mvvm.distDirectory}/usr/share/java/reports")

BuildEnv::ALPINE.installTarget.installDirs("#{mvvm.distDirectory}/usr/share/metavize/toolbox")

mvvm_cacerts = "#{mvvm.distDirectory}/usr/share/metavize/conf/cacerts"
java_cacerts = "#{BuildEnv::JAVA_HOME}/jre/lib/security/cacerts"
mv_ca = "#{ALPINE_HOME}/mvvm/resources/mv-ca.pem"
ut_ca = "#{ALPINE_HOME}/mvvm/resources/ut-ca.pem"

file mvvm_cacerts => [ java_cacerts, mv_ca, ut_ca ] do
  ensureDirectory(File.dirname(mvvm_cacerts))
  FileUtils.cp(java_cacerts, mvvm_cacerts)
  FileUtils.chmod(0666, mvvm_cacerts)
  Kernel.system("#{BuildEnv::JAVA_HOME}/bin/keytool", '-import', '-noprompt',
                '-keystore', mvvm_cacerts, '-storepass', 'changeit', '-file',
                mv_ca, '-alias', 'metavizeprivateca')
  Kernel.system("#{BuildEnv::JAVA_HOME}/bin/keytool", '-import', '-noprompt',
                '-keystore', mvvm_cacerts, '-storepass', 'changeit', '-file',
                ut_ca, '-alias', 'untangleprivateca')
end

if BuildEnv::ALPINE.isDevel
  BuildEnv::ALPINE.installTarget.installFiles('debian/control', "#{mvvm.distDirectory}/tmp",
                              'pkg-list')

  activationKey = "#{mvvm.distDirectory}/usr/share/metavize/activation.key"

  ## Insert the activation key if necessary.  Done here to not include
  ## The file inside of packages
  file activationKey do
    File.open( activationKey, "w" ) { |f| f.puts( "0000-0000-0000-0000" ) }
  end

  BuildEnv::ALPINE.installTarget.registerDependency(activationKey)
end

BuildEnv::ALPINE.installTarget.registerDependency(mvvm_cacerts)

deps  = Jars::Base + Jars::TomcatEmb + Jars::JavaMail + Jars::Jcifs +
  Jars::Dom4j + Jars::Activation + Jars::Trove + Jars::Junit + Jars::WBEM +
  [ mvvm['bootstrap'], mvvm['api'], mvvm['localapi'], mvvm['impl'], jnetcap['impl'], jvector['impl']]

JarTarget.buildTarget(BuildEnv::ALPINE["unittest"], deps, 'mvvm', "#{ALPINE_HOME}/mvvm/unittest")
