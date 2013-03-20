# -*-ruby-*-

class NodeBuilder

  def NodeBuilder.makeNode(buildEnv, name, location, deps = [], baseHash = {})
    makePackage(buildEnv, name, location, deps, baseHash)
  end

  def NodeBuilder.makeCasing(buildEnv, name, location, deps = [], baseHash = {})
    makePackage(buildEnv, name, location, deps, baseHash)
  end

  def NodeBuilder.makeBase(buildEnv, name, location, deps = [], baseHash = {})
    makePackage(buildEnv, name, location, deps, baseHash)
  end

  private
  ## Create the necessary packages and targets to build a node
  def NodeBuilder.makePackage(buildEnv, name, location, deps = [], baseHash = {})
    home = buildEnv.home

    dirName = location
    node = buildEnv["#{name}"]
    buildEnv.installTarget.register_dependency(node)
    buildEnv['node'].registerTarget("#{name}", node)

    ## If there is an SRC, build it first
    src = FileList["#{home}/#{dirName}/src/**/*.java"]
    baseHash.each_pair do |bd, bn|
      src += FileList["#{bn.buildEnv.home}/#{bd}/src/**/*.java"]
    end

    if (src.length > 0)
      deps  = baseJars + deps

      paths = baseHash.map { |bd, bn| ["#{bn.buildEnv.home}/#{bd}/src", "#{bn.buildEnv.home}/#{bd}/src"] }.flatten

      srcJar = JarTarget.build_target(node, deps, 'src', ["#{home}/#{dirName}/src"] + paths)
      buildEnv.installTarget.install_jars(srcJar, "#{node.distDirectory}/usr/share/untangle/toolbox", nil, true)
    end

    po_dir = "#{home}/#{dirName}/po"
    if File.exist? po_dir
      JavaMsgFmtTarget.make_po_targets(node, po_dir, "#{node.distDirectory}/usr/share/untangle/lang/", name).each do |t|
        buildEnv.i18nTarget.register_dependency(t)
      end
    end

    hierFiles = FileList["#{home}/#{dirName}/hier/**/*"]
    if ( hierFiles.length > 0 )
      ms = MoveSpec.new("#{home}/#{dirName}/hier", hierFiles, node.distDirectory)
      cf = CopyFiles.new(node, ms, 'hier', buildEnv.filterset)
      buildEnv.hierTarget.register_dependency(cf)

      # uncomment this to copy all python2.6 to python2.7 (for wheezy support)
      # ms_python = MoveSpec.new("#{home}/#{dirName}/hier/usr/lib/python2.6", FileList["#{home}/#{dirName}/hier/usr/lib/python2.6/**/*"], "#{node.distDirectory}/usr/lib/python2.7/")
      # cf_python = CopyFiles.new(node, ms_python, 'python2.7', buildEnv.filterset)
      # buildEnv.hierTarget.register_dependency(cf_python)
    end

    jsFiles = FileList["#{home}/#{dirName}/hier/usr/share/untangle/web/webui/**/*.js"]
    if ( jsFiles.length > 0 ) 
      jsFiles.each do |f|
        jsl = JsLintTarget.new(node, [f], 'jslint', f)
        buildEnv.jsLintTarget.register_dependency(jsl)
      end
    end
  end

  ## Helper to retrieve the standard dependencies
  def NodeBuilder.baseJars
    uvm_lib = BuildEnv::SRC['untangle-libuvm']
    Jars::Base + [Jars::JFreeChart, uvm_lib['api']]
  end
end
