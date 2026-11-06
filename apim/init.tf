terraform {
  required_version = ">= 1.16.1, < 2.0.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "5.13.0"
    }
  }
  backend "azurerm" {}
}

provider "azurerm" {
  features {}
}
