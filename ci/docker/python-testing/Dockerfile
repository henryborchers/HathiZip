FROM python:3.7

ADD requirements.txt  ./
ADD requirements-dev.txt  ./

RUN pip install --upgrade pip && \
    pip install --no-cache-dir \
    bandit \
    flake8 \
    coverage \
    pylint \
     -r requirements.txt \
     -r requirements-dev.txt
