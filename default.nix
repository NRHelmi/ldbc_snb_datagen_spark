{
  pkgs ? import (builtins.fetchTarball "https://github.com/NixOS/nixpkgs/archive/bc66bad58ccceccae361e84628702cfc7694efda.tar.gz") {},
  sf ? "0.003"
}:

let

  spark-hadoop = builtins.fetchTarball "https://downloads.apache.org/spark/spark-3.1.2/spark-3.1.2-bin-hadoop3.2.tgz";

in with pkgs;
stdenv.mkDerivation rec {
  name = "ldbc_snb_datagen_spark_${sf}";
  src = ./.;
  buildInputs = [ maven openjdk8 python38Packages.virtualenv ];
  buildPhase = ''
    mkdir -p $out/tmp

    export SPARK_HOME="${spark-hadoop}"
    export PATH="$PATH":"$SPARK_HOME/bin"

    virtualenv env
    source env/bin/activate
    
    pip install ./tools

    tools/build.sh

    tools/run.py \
      ./target/ldbc_snb_datagen_*-SNAPSHOT-jar-with-dependencies.jar -- \
      --format csv \
      --scale-factor ${sf} \
      --mode bi \
      --output-dir $out/tmp

    cp -r $out/tmp/graphs/csv/bi/composite-merged-fk/* $out
    rm -rf $out/tmp

    cd $out/initial_snapshot

    echo "Merging outputs ..."
    for i in static/*; do
      echo "- $i"
      cat $i/part-00000*.csv > $i.csv
      # Delete the file so we don't rejoin it
      rm $i/part-00000*.csv
      tail -qn +2 $i/part-*.csv >> $i.csv
      rm -rf $i
    done

    for i in dynamic/*; do
      echo "- $i"
      cat $i/part-00000*.csv > $i.csv
      # Delete the file so we don't rejoin it
      rm $i/part-00000*.csv
      tail -qn +2 $i/part-*.csv >> $i.csv
      rm -rf $i
      echo "- $i"
    done
  '';

  shellHook = ''
    export SPARK_HOME="${spark-hadoop}"
    export PATH="$PATH":"$SPARK_HOME/bin"
  '';

  dontInstall = true;

  __noChroot = true;
}
