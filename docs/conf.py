# Configuration file for the Sphinx documentation builder.
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os

# -- Project information --------------------------------------------------

project   = "Honey Badger Flight Logistics"
copyright = "2026, Honey Badger Flight Logistics"
author    = "Honey Badger Flight Logistics"
release   = os.environ.get("PROJECT_VERSION", "0.0.1-SNAPSHOT")
version   = release.split("-")[0]

# -- General configuration ------------------------------------------------

extensions = ["sphinx.ext.mathjax"]

templates_path   = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

# -- Options for HTML output -----------------------------------------------

html_theme = "sphinx_rtd_theme"
html_static_path = ["_static"]

html_theme_options = {
    "navigation_depth": 4,
    "titles_only": False,
}
