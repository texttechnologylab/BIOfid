# BIOfid Dataset
## Publishing a German Gold Standard for Named Entity Recognition in Historical Biodiversity Literature

The [BIOfid project](https://www.biofid.de/en/) has been launched to mobilize valuable biological data from printed literature hidden in German libraries for over the past 250 years. In this repository, we publish the newly annotated *BIOfid dataset* for *Named Entity Recognition* (NER) and for *Taxa Recognition* (TR) in the domain of biology, the first of its kind concerning the German language. We upload the resources (under the [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/legalcode) license) including the taxonomic gazetteers for a TR-baseline, the source code for preprocessing the BIOfid corpus, and the full annotation guidlines which were used for generating the BIOfid dataset.

Further resources utilized in our study are listed as follows:

## Requirements
- [BiLSTM Tagger](https://github.com/glample/tagger)
- Python 3

## Data
### Pre-trained Word Embeddings
- [COW Wang2vec](https://www.texttechnologylab.org/resources2018/) or reproduction from scratch by following the repository [GermanWordEmbeddings-NER](https://github.com/texttechnologylab/GermanWordEmbeddings-NER).
### Pre-trained Language Models
- [PCE (de-X, German)](https://github.com/zalandoresearch/flair/blob/master/resources/docs/embeddings/FLAIR_EMBEDDINGS.md)
- PCE + ELMo (trained on the [Leipzig corpus](http://wortschatz.uni-leipzig.de/en/download))
- PCE + [BERT (bert-base-multilingual-cased)](https://github.com/zalandoresearch/flair/blob/master/resources/docs/embeddings/TRANSFOMER_EMBEDDINGS.md)
### Labeled datasets for German named entity recognition
- [CoNLL 2003](https://www.clips.uantwerpen.be/conll2003/ner/)
- [GermEval 2014](https://sites.google.com/site/germeval2014ner/data)


Further information on the source text, the prepreocessing pipline, the annotations data and its evaluation can be found in Ahmed et al. (2019). Please cite this study if you happen to use its resources in your work. In case of further questions, do not hesitate to contact the first author.

## Cite
S. Ahmed, M. Stoeckel, C. Driller, A. Pachzelt, and A. Mehler, “BIOfid Dataset: Publishing a German Gold Standard for Named Entity Recognition in Historical Biodiversity Literature,” in Proceedings of the 23rd Conference on Computational Natural Language Learning (CoNLL), 2019.

## BibTeX

```
@InProceedings{Ahmed:Stoeckel:Driller:Pachzelt:Mehler:2019,
author = {Sajawel Ahmed and Manuel Stoeckel and Christine Driller and Adrian Pachzelt and Alexander Mehler},
title = {{BIOfid Dataset: Publishing a German Gold Standard for Named Entity Recognition in Historical Biodiversity Literature}},
booktitle = {Proceedings of the 23rd Conference on Computational Natural Language Learning (CoNLL)},
location = {Hong Kong, China},
year = 2019,
note = {accepted}
}
```
