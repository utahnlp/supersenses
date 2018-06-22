This repository produces the results of the feature rich classifier from
the paper:

Schneider, Nathan, Jena D. Hwang, Vivek Srikumar, Jakob Prange, Austin
Blodgett, Sarah R. Moeller, Aviram Stern, Adi Bitan, and Omri
Abend. "Comprehensive Supersense Disambiguation of English Prepositions
and Possessives." In ACL (2018).


## Getting started

To run this code, you will need the following installed on your
computer:

1. Scala and SBT
2. Liblinear

If you are on a Mac, you can use home brew to install these
dependencies using `brew install scala sbt liblinear`.

Next, clone this repository. Included as a git submodule is the
supersense dataset that was released with the paper. In addition to
the train/dev/test data splits, that repository also contains the
official evaluation scripts for the data.

After cloning the repository, compile everything using `sbt compile`
on the terminal. Now, you can train the classifiers.


## Instructions for training classifiers

The pipeline for training scene role and function classifiers is:

1. Preprocess the data
2. Extract features into liblinear compatible files
3. Train the scene role and function classifiers
4. Construct the output in the json format for evaluation using the
   official script.

Other than step 3, these steps use the scala code in this repository
and can be invoked using `scripts/run.sh`. This script provides an
entry point to three sub-commands `extract`, `preprocess` and
`write-json` which corresponds to steps 1, 2 and 4 respectively.

1. `run.sh preprocess` reads the streusle data and runs preprocessing
   tools on it.
2. `run.sh extract` performs feature extraction so that we can train
   the classifiers. It also saves enough metadata so that we can
   reconstruct json files for evaluation.
3. `run.sh write-json` converts the liblinear prediction files into
   json formatted output for evaluation.

Each of these sub-commands take arguments involving the location of
the data files. Running the sub-command without any arguments will
describe required arguments.


## Running experiments
`scripts/experiment.sh` is a single bash script that performs all the
steps described above and keeps track of the intermediate files that
are generated along the way. This script will write the data files,
models and predicted supersenses to `experiments/output`.

The paper compares the impact of automatically identified prepositions
and predicted parse trees. To train classifiers in these four settings
use:

1. `scripts/experiment.sh --goldid --goldsyn`
2. `scripts/experiment.sh --goldid --autosyn`
3. `scripts/experiment.sh --autoid --goldsyn`
4. `scripts/experiment.sh --autoid --autosyn`

After running these four commands in a row, if all goes well, you
will find four json files in `experiments/output`. Each file
contains predictions for that setting.

If you want to modify the training data to produce learning curves,
etc, you will have to edit the `TRAIN_JSON` variable in the
experiment script.
