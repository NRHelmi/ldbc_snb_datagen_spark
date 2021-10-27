{
  pkgs ? import (builtins.fetchTarball "https://github.com/NixOS/nixpkgs/archive/bc66bad58ccceccae361e84628702cfc7694efda.tar.gz") {},
  sf ? "0.1"
}:

let

  spark-hadoop = builtins.fetchTarball "https://downloads.apache.org/spark/spark-3.1.2/spark-3.1.2-bin-hadoop3.2.tgz";

in with pkgs;
stdenv.mkDerivation rec {
  name = "ldbc_snb_datagen_spark";
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
      --mode raw \
      --output-dir $out/tmp

    mv $out/tmp/graphs/csv/raw/composite-merged-fk/* $out
    rm -rf $out/tmp

    cd $out

    echo "Merging outputs ..."
    for i in static/*; do
      echo "- $i"
      head -n 1 $i/part_0_0.csv > $i.csv
      tail -qn +2 $i/*.csv >> $i.csv
      rm -rf $i
    done

    for i in dynamic/*; do
      echo "- $i"
      head -n 1 $i/part_0_0.csv > $i.csv
      tail -qn +2 $i/*.csv >> $i.csv
      rm -rf $i
    done
  '';

  shellHook = ''
    export SPARK_HOME="${spark-hadoop}"
    export PATH="$PATH":"$SPARK_HOME/bin"
  '';

  dontInstall = true;

  __noChroot = true;
}
