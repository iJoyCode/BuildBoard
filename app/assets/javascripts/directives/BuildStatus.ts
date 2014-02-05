/// <reference path="../_all.ts" />
module buildBoard {
    export class BuildStatusDirective implements ng.IDirective {
        static NAME = "buildStatus";

        scope = {
            build: "=",
            buildActions: "=",
            branch: "=",
            type: "@"
        };

        controller = LastBuildStatusController;

        template =
            '<div class="dropdown">' +
                '<a ng-show="build" href="" class="jenkins status {{type}} {{ getBuildStatus(build) | status }} dropdown-toggle" data-toggle="dropdown"' +
                ' title="{{getBuildStatus(build)}}">' +
                '<span ng-show="buildActions">{{ build.timeStamp | date }}</span>' +
                '<span ng-hide="buildActions">{{ getBuildStatus(build) }}</span>' +
                '</a>' +
                '<a ng-hide="build" href="" class="jenkins status {{type}} dropdown-toggle" data-toggle="dropdown">' +
                'Never built' +
                '</a>' +
                '<ul class="dropdown-menu">' +
                '<li ng-show="build"><a href="{{build.url}}" class="jenkins active">Go to Jenkins</a></li>' +
                '<li ng-show="build"><a href="" ng-click="toggleBuild(branch, build)" class="jenkins">Toggle build</a></li>' +
                '<hr ng-show="build && buildActions">'+
                '<li ng-repeat="action in buildActions">' +
                '<a class="jenkins" href=""ng-click="forceBuild(action)">{{action.name}}</a>' +
                '</li>' +
                '</ul>' +
                '</div>';

        restrict = "E";
        replace = true;
    }

    export class LastBuildStatusController {
        public static $inject = ['$scope', BackendService.NAME];

        constructor(private $scope:any, backendService:BackendService) {
            var statusFilter = status();

            this.$scope.forceBuild = (buildAction:BuildAction) => {
                backendService.forceBuild(buildAction).success(build=> {
                    this.$scope.build = build;
                });
            };

            this.$scope.getBuildStatus = (build:Build)=> {
                if (!build) {
                    return "";
                }
                if (build.toggled)
                    return "Toggled";
                else
                    return build.status;
            };

            this.$scope.toggleBuild = (branch:Branch, build:Build)=>{
                console.log(build);
                backendService.toggleBuild(branch.name, build.number).success(b=>{
                    build.toggled=!build.toggled;
                });
            }
        }
    }
}