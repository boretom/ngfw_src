# -*-ruby-*-

# Certified Filthy2007
ENV["JAVA_HOME"] = "/usr/lib/jvm/java-1.5.0-sun"

$DevelBuild = true
$CleanBuild = false
ARGV.each do |arg|
  if arg =~ /install/
    $DevelBuild = false
  end

  if arg =~ /clean/
    $CleanBuild = true
  end
end

## This is how you define where the stamp file will go
module Rake
  StampFile = "#{ALPINE_HOME}/taskstamps.txt"
end

require "#{ALPINE_HOME}/buildtools/stamp-task.rb"
require "#{ALPINE_HOME}/buildtools/rake-util.rb"
require "#{ALPINE_HOME}/buildtools/c-compiler.rb"
require "#{ALPINE_HOME}/buildtools/jars.rb"
require "#{ALPINE_HOME}/buildtools/jasper.rb"
require "#{ALPINE_HOME}/buildtools/transform.rb"

## Require all of the sub packages.
## Done manually because order matters.
## XXX Could create a new helper method that sets a prefix directory before
## calling require and then unsets it afterwards.
require "#{ALPINE_HOME}/util/package.rb"
require "#{ALPINE_HOME}/libmvutil/package.rb"
require "#{ALPINE_HOME}/libnetcap/package.rb"
require "#{ALPINE_HOME}/libvector/package.rb"
require "#{ALPINE_HOME}/jmvutil/package.rb"
require "#{ALPINE_HOME}/jnetcap/package.rb"
require "#{ALPINE_HOME}/jvector/package.rb"
require "#{ALPINE_HOME}/nfutil/package.rb"
require "#{ALPINE_HOME}/mvvm/package.rb"
require "#{ALPINE_HOME}/gui/package.rb"
require "#{ALPINE_HOME}/tran/package.rb"

libalpine_so = "#{BuildEnv::ALPINE.staging}/libalpine.so"

archives = ['libmvutil', 'libnetcap', 'libvector', 'jmvutil', 'jnetcap', 'jvector']

## Make the so dependent on each archive
archives.each do |n|
  file libalpine_so => BuildEnv::ALPINE[n]['archive']
end

file libalpine_so do
  compilerEnv = CCompilerEnv.new( { "flags" => "-pthread #{CCompilerEnv.defaultDebugFlags}" } )
  archivesFiles = archives.map { |n| BuildEnv::ALPINE[n]['archive'].filename }

  CBuilder.new(BuildEnv::ALPINE, compilerEnv).makeSharedLibrary(archivesFiles, libalpine_so, [],
                                              ['xml2', 'sysfs', 'netfilter_queue'], ['ipq'])
end

BuildEnv::ALPINE['mvvm']['impl'].registerDependency(libalpine_so)

BuildEnv::ALPINE.installTarget.installFiles(libalpine_so, "#{BuildEnv::ALPINE['libalpine'].distDirectory}/usr/lib/mvvm")

# DO IT!
#graphViz('foo.dot')
