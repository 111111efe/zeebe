// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cmd

import (
	"github.com/spf13/cobra"
	"github.com/zeebe-io/zeebe/clients/zbctl/utils"
	"log"
)

var completeJobPayloadFlag string

// completeJobCmd represents the completeJob command
var completeJobCmd = &cobra.Command{
	Use:   "job <jobKey>",
	Short: "Complete a job",
	Args: cobra.ExactArgs(1),
	PreRun: initBroker,
	Run: func(cmd *cobra.Command, args []string) {
		jobKey := convertToKey(args[0], "Expect job key as only positional argument, got")

		request, err := client.NewCompleteJobCommand().JobKey(jobKey).PayloadFromString(completeJobPayloadFlag)
		utils.CheckOrExit(err, utils.ExitCodeConfigurationError, defaultErrCtx)

		_, err = request.Send()
		log.Println("Completed job with key", jobKey, "and payload", completeJobPayloadFlag)
	},
}

func init() {
	completeCmd.AddCommand(completeJobCmd)
	completeJobCmd.Flags().StringVar(&completeJobPayloadFlag, "payload", utils.EmptyJsonObject, "Specify payload as JSON object string")
}
