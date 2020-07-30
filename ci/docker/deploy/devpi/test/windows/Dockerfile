# escape=`
ARG PYTHON_DOCKER_IMAGE_BASE=python:3.7
FROM ${PYTHON_DOCKER_IMAGE_BASE} as wheel_builder

ARG PIP_EXTRA_INDEX_URL
ARG PIP_TRUSTED_HOST
ARG PIP_INDEX_URL

RUN python -m pip install --upgrade pip ; `
    pip install wheel ; `
    pip install --upgrade setuptools
COPY requirements.txt requirements-dev.txt requirements-freeze.txt ci/docker/shared/requirements-ci.txt c:/requirements/
RUN pip wheel -w c:\wheels -r c:\requirements\requirements.txt `
                           -r c:\requirements\requirements-dev.txt `
                           -r c:\requirements\requirements-freeze.txt `
                           -r c:\requirements\requirements-ci.txt

FROM ${PYTHON_DOCKER_IMAGE_BASE}
RUN certutil -generateSSTFromWU roots.sst ; `
    certutil -addstore -f root roots.sst ; `
    del roots.sst
COPY --from=wheel_builder c:\wheels c:/wheels/
RUN python -m pip install --upgrade pip ; `
    pip install wheel ; `
    pip install --upgrade setuptools
COPY requirements.txt requirements-dev.txt requirements-freeze.txt ci/docker/shared/requirements-ci.txt c:/requirements/

RUN python -m pip install --no-index --find-links c:\wheels `
       -r c:\requirements\requirements.txt `
       -r c:\requirements\requirements-dev.txt `
       -r c:\requirements\requirements-freeze.txt `
       -r c:\requirements\requirements-ci.txt

ENV PIP_FIND_LINKS=c:\wheels
