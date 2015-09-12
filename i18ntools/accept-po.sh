#!/bin/bash

if [ $# -ne 4 ]; then
    echo "usage $0 po.zip work hades internal"
    exit -1
fi

po="$1"
work="$2"
hades="$3"
internal="$4"

t=$(mktemp -d)
unzip -d $t "$po"

for f in $(find $t -type f -name '*.po'); do
    c=$(basename $(dirname $f))
    m=$(basename $f .po)

    d=""

    case $m in
        untangle-apache2-config)
            d=$work/pkgs/untangle-apache2-config
            ;;
        untangle-base-virus-blocker)
            d=$work/src/virus-blocker-base
            ;;
        untangle-base-web-filter)
            d=$work/src/web-filter-base
            ;;
        untangle-casing-smtp)
            d=$work/src/smtp-casing
            ;;
        untangle-install-wizard)
            d=$work/src/gui
            ;;
        untangle-libuvm)
            d=$work/src/uvm
            ;;
        untangle-node-ad-blocker)
            d=$work/src/ad-blocker
            ;;
        untangle-node-directory-connector)
            d=$hades/src/directory-connector
            ;;
        untangle-node-bandwidth)
            d=$hades/src/bandwidth
            ;;
        untangle-node-configuration-backup)
            d=$hades/src/configuration-backup
            ;;
        untangle-node-branding)
            d=$hades/src/branding
            ;;
        untangle-node-spam-blocker)
            d=$hades/src/spam-blocker
            ;;
        untangle-node-virus-blocker)
            d=$hades/src/virus-blocker
            ;;
        untangle-node-faild)
            d=$hades/src/faild
            ;;
        untangle-node-firewall)
            d=$work/src/firewall
            ;;
        untangle-node-ips)
            d=$work/src/ips
            ;;
            ;;
        untangle-node-license)
            d=$hades/src/license
            ;;
        untangle-node-openvpn)
            d=$work/src/openvpn
            ;;
        untangle-node-phish-blocker)
            d=$work/src/phish-blocker
            ;;
        untangle-node-policy)
            d=$hades/src/policy
            ;;
        untangle-node-application-control-lite)
            d=$work/src/application-control-lite
            ;;
        untangle-node-reporting)
            d=$work/src/reporting
            ;;
        untangle-node-shield)
            d=$work/src/shield
            ;;
        untangle-node-sitefilter)
            d=$hades/src/sitefilter
            ;;
        untangle-node-spam-blocker-lite)
            d=$work/src/spam-blocker-lite
            ;;
        untangle-node-splitd)
            d=$hades/src/splitd
            ;;
        untangle-node-support)
            d=$hades/src/support
            ;;
        untangle-node-webcache)
            d=$hades/src/webcache
            ;;
        untangle-node-ipsec)
            d=$hades/src/ipsec
            ;;
        untangle-node-classd)
            d=$hades/src/classd
            ;;
        untangle-node-web-filter-lite)
            d=$work/src/web-filter-lite
            ;;
        untangle-system-stats*)
            d=$internal/isotools/installer-pkgs-additional/untangle-system-stats/debian
            ;;
        *)
            echo "unknown module: $m"
    esac

    if [ "x$d" != "x" ]; then
        i="$d/po/$c/"
        mkdir -p $i
        cp $f $i
        svn add $i
    fi
done

echo "remember to run svn commit in $work, $hades, and $internal"
